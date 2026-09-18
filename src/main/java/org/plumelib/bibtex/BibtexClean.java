package org.plumelib.bibtex;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.Pattern;
import org.plumelib.util.EntryReader;
import org.plumelib.util.FilesP;

/**
 * Clean a BibTeX file by removing text outside BibTeX entries.
 *
 * <p>Remove each non-empty line that is not in a BibTeX entry, except retain any line that starts
 * with "%". Each BibTeX entry should not contain blank lines.
 *
 * <p>Arguments are the names of the original files. Cleaned copies of those files are written in
 * the CURRENT DIRECTORY. Therefore, this should be run in a different directory from where the
 * argument files are. If an argument file is in the current directory, the program writes a
 * diagnostic and exits rather than overwriting the file.
 */

// The implementation copies lines verbatim, recognizing an entry by its "@"
// line and by counting delimiters, rather than using a BibTeX parser, because
// BibTeX parsers generally do not preserve formatting, such as indentation,
// delimiter characters, and order of fields.  And, the ones I looked at were
// not very well documented.

// The implementation uses EntryReader only to iterate through the lines of
// the file; it does not use EntryReader's notion of an "entry".  That is
// because the @ line does not necessarily follow a blank line -- there
// might be a comment line before it.  But, EntryReader requires that its
// "long entries" start after a blank line.  (That can be considered an
// EntryReader bug, or at least inflexibility in its interface.)

public final class BibtexClean {

  /** This class is a collection of methods; it does not represent anything. */
  private BibtexClean() {
    throw new Error("do not instantiate");
  }

  /** Regex for the start of a BibTeX entry. BibTeX permits whitespace before the "@". */
  private static final Pattern entryStart = Pattern.compile("^[ \t]*@");

  /**
   * Regex for a line that contains only an entry type, such as "@article". BibTeX permits
   * whitespace, including a line separator, between the entry type and the entry's opening
   * delimiter.
   *
   * <p>BibTeX permits an entry type to contain any character other than whitespace and {@code
   * "#%'(),={}}, but this regex permits only a letter, digit, hyphen, or underscore. A stricter
   * regex is better here, because a line that this regex matches starts a search for a delimiter
   * that might be far away or absent. For example, this regex does not match a line of ordinary
   * text that starts with an email address, such as "@example.com is my address".
   */
  private static final Pattern entryTypeOnly =
      Pattern.compile("^[ \t]*@[A-Za-z][A-Za-z0-9_-]*[ \t]*$");

  /**
   * Clean a BibTeX file by removing text outside BibTeX entries.
   *
   * @param args names of the original files. The original files should be in a different directory
   *     than the working directory.
   */
  public static void main(String[] args) {
    for (String filename : args) {
      File inFile = new File(filename);
      File outFile = new File(inFile.getName()); // in current directory
      // The input file is opened before the output file is created (and before the output file is
      // deleted), so that a run whose input cannot be opened -- because the input does not exist,
      // or is the output file -- leaves the current directory unchanged.  In particular, such a
      // run does not destroy the output of a previous, successful run.  (A failure that occurs
      // later, while reading or writing, does replace the previous output.)
      try (EntryReader er = openInput(inFile, outFile)) {
        // Delete the file to work around a bug.  Files.newBufferedWriter (which is called by
        // FilesP.newBufferedFileWriter) seems to have a bug where it does not correctly truncate
        // the file first.  If the target file already exists, then characters beyond what is
        // written remain in the file.
        outFile.delete();
        boolean writeFailed;
        // `bw`, rather than the PrintWriter that wraps it, is the resource, so that an IOException
        // thrown while closing the file is propagated rather than being suppressed by PrintWriter.
        try (BufferedWriter bw = FilesP.newBufferedFileWriter(outFile.toString())) {
          PrintWriter out = new PrintWriter(bw);
          clean(er, out, System.err);
          // PrintWriter suppresses IOException, so ask it whether writing succeeded.  `checkError`
          // flushes `out`, so this accounts for everything that `clean` wrote.
          writeFailed = out.checkError();
        }
        // Exit after the try-with-resources statement rather than within it, so that the file is
        // closed.  Closing matters even on failure: for a compressed output file, closing writes
        // the trailer, without which the file cannot be read at all.
        if (writeFailed) {
          System.err.printf("Problem writing %s%n", outFile);
          System.exit(2);
        }
        // EntryReader's iterator wraps an IOException that occurs while reading a line in an
        // UncheckedIOException, so catching only IOException would let such a failure escape as a
        // stack trace.
      } catch (IOException | UncheckedIOException e) {
        System.err.printf(
            "Problem reading %s or writing %s: %s%n", inFile, outFile, e.getMessage());
        System.exit(2);
      }
    }
  }

  /**
   * Returns a reader for the given input file. Exits the program if the input file is also the
   * output file, because cleaning such a file would destroy it.
   *
   * @param inFile the file to read
   * @param outFile the file that the cleaned copy of {@code inFile} will be written to
   * @return a reader for {@code inFile}
   * @throws IOException if {@code inFile} cannot be opened, or if either file name cannot be
   *     canonicalized
   */
  private static EntryReader openInput(File inFile, File outFile) throws IOException {
    if (inFile.getCanonicalFile().equals(outFile.getCanonicalFile())) {
      System.err.printf(
          "Input file %s is also the output file; run in a different directory.%n", inFile);
      System.exit(2);
    }
    return new EntryReader(inFile.toString());
  }

  /**
   * Copy BibTeX from {@code er} to {@code out}, removing text outside BibTeX entries. Write
   * diagnostics about unterminated entries to {@code err}.
   *
   * <p>This method does not close {@code er}, {@code out}, or {@code err}; the caller retains
   * ownership of all three.
   *
   * <p>This method is package-private, rather than private, so that tests can call it without
   * writing to the file system.
   *
   * @param er the BibTeX to read
   * @param out where to write the cleaned BibTeX
   * @param err where to write diagnostics about unterminated entries
   */
  static void clean(EntryReader er, PrintWriter out, PrintStream err) {
    for (String line : er) {
      if (line.isEmpty() || line.startsWith("%")) {
        out.println(line);
      } else if (entryStart.matcher(line).lookingAt()) {
        out.println(line);
        EntryState state = new EntryState();
        state.update(line);
        if (state.isComplete()) {
          continue;
        }
        if (!state.isStarted() && !entryTypeOnly.matcher(line).matches()) {
          // A line that starts with "@", opens no delimiter, and has other text on it -- which is
          // not a well-formed entry -- is an entry all by itself, and the text after it is treated
          // as being outside any entry.  By contrast, a line that holds only the entry type may be
          // an entry whose opening delimiter is on a later line, so scanning continues below.
          continue;
        }
        String entryStartLine = line;
        // Capture the file name and line number before the loop below, because reaching
        // end of input closes the reader, after which `er.getFileName()` and
        // `er.getLineNumber()` throw an exception.
        String entryStartFileName = er.getFileName();
        int entryStartLineNumber = er.getLineNumber();
        // Copy the rest of the entry.  Each way of leaving this loop writes its own
        // diagnostic, if any, so no bookkeeping variable is needed.
        while (true) {
          if (!er.hasNext()) {
            err.printf(
                "%s:%d: unterminated entry at EOF: %s%n",
                entryStartFileName, entryStartLineNumber, entryStartLine);
            break;
          }
          String line2 = er.next(); // not null because `er.hasNext()` returned true
          out.println(line2);
          if (line2.isEmpty()) {
            err.printf(
                "%s:%d: unterminated entry: %s%n",
                entryStartFileName, entryStartLineNumber, entryStartLine);
            break;
          }
          state.update(line2);
          if (state.isComplete()) {
            break;
          }
        }
      }
    }
  }

  /**
   * The state of scanning a BibTeX entry: the closing delimiters that the entry still awaits,
   * whether the scan is currently within a quote-delimited field value, and the parentheses that
   * are ordinary text within a parenthesis-delimited entry.
   *
   * <p>A new {@code EntryState} has not started, because the entry's opening delimiter has not been
   * seen. The entry ends when, having started, it awaits no closing delimiter.
   */
  private static final class EntryState {

    /** Creates a new {@code EntryState} that has not yet seen an opening delimiter. */
    public EntryState() {}

    /** The closing delimiters that the entry still awaits, innermost first. */
    private final Deque<Character> pendingDelimiters = new ArrayDeque<>();

    /** True if the entry's own opening delimiter has been seen. */
    private boolean started = false;

    /** True if the scan is within a quote-delimited field value, such as {@code "A Title"}. */
    private boolean inQuotedValue = false;

    /**
     * The number of "{" characters within the current quote-delimited field value that have not yet
     * been matched by a "}". A quotation mark ends the value only where this is zero, because a
     * quotation mark within braces is ordinary text, as in {@code author = "Schl{\"o}mer"}.
     */
    private int quotedValueBraces = 0;

    /**
     * The number of "(" characters that are ordinary text, appear directly within a
     * parenthesis-delimited entry, and have not yet been matched by a ")". Each such ")" is
     * ordinary text rather than the delimiter that closes the entry.
     */
    private int ordinaryParens = 0;

    /**
     * The most recent non-blank character that has been scanned, or {@code '\0'} if there is none.
     * A line separator is a blank, so this can be a character from a previous line.
     */
    private char previousNonBlank = '\0';

    /**
     * Returns true if the entry's own opening delimiter has been seen.
     *
     * @return true if the entry has started
     */
    public boolean isStarted() {
      return started;
    }

    /**
     * Returns true if the entry has started and awaits no closing delimiter; that is, if the entry
     * has ended.
     *
     * @return true if the entry has ended
     */
    public boolean isComplete() {
      return started && pendingDelimiters.isEmpty();
    }

    /**
     * Returns true if the scan is directly within a parenthesis-delimited entry; that is, if the
     * only pending delimiter is the entry's own ")".
     *
     * @return true if the only pending delimiter is the entry's own ")"
     */
    private boolean inParenEntryTopLevel() {
      Character innermost = pendingDelimiters.peek();
      return pendingDelimiters.size() == 1 && innermost != null && innermost == ')';
    }

    /**
     * Updates this for the delimiters that appear in {@code line}: pushes the matching closing
     * delimiter for each "{" or "(" that opens one, and pops for each "}" or ")" that matches the
     * innermost pending delimiter.
     *
     * <p>A character that cannot be a delimiter where it appears is ordinary text, and is ignored:
     *
     * <ul>
     *   <li>any delimiter within a quote-delimited field value, as is the "}" in {@code title = "A
     *       } brace"};
     *   <li>a quotation mark within braces within a quote-delimited field value, as is the one in
     *       {@code author = "Schl{\"o}mer"}; such a quotation mark does not end the value, so the
     *       braces within a quoted value are counted even though they are not delimiters;
     *   <li>a quotation mark that does not start a field value, as is the one in {@code note = 5"
     *       floppy disk}; a field value starts only at the top level of the entry, and only just
     *       after "=", "#", or the entry's own opening delimiter;
     *   <li>a "(", except as the entry's own opening delimiter as in {@code @article(key, ...)};
     *       BibTeX gives parentheses no meaning within an entry, as in a value that ends with a
     *       frown;
     *   <li>a ")" that matches such an ordinary "(", as in {@code @article(key, note = see (Smith
     *       1999), ...)};
     *   <li>a closing delimiter that does not match the innermost pending one, as is the ")" in a
     *       braced value that ends with a smiley.
     * </ul>
     *
     * <p>An unmatched ordinary "(" directly within a parenthesis-delimited entry makes the entry's
     * own ")" look like ordinary text, so the entry is not recognized as ending. That is the safe
     * direction of error: the program retains the text and writes a diagnostic, rather than
     * silently discarding text.
     *
     * <p>A backslash does not protect the character after it. BibTeX counts braces without regard
     * to backslashes, so doing the same here makes this agree with BibTeX about where an entry
     * ends, even for an entry whose braces BibTeX would consider unbalanced.
     *
     * <p>Once the entry has ended, the rest of the line is not examined.
     *
     * @param line the line to scan
     */
    public void update(String line) {
      for (int i = 0; i < line.length(); i++) {
        char c = line.charAt(i);
        if (inQuotedValue) {
          // The braces within a quoted value are not delimiters, but they are counted, because a
          // quotation mark within them is ordinary text rather than the end of the value.  That is
          // how BibTeX reads a value such as "Schl{\"o}mer".  A "}" that matches no "{" is ignored
          // rather than counted, as in the value "A } brace".
          if (c == '{') {
            quotedValueBraces++;
          } else if (c == '}') {
            if (quotedValueBraces > 0) {
              quotedValueBraces--;
            }
          } else if (c == '"' && quotedValueBraces == 0) {
            inQuotedValue = false;
          }
        } else if (c == '"') {
          // A quotation mark delimits a field value only where a value can start.  Elsewhere it is
          // ordinary text, as in an abstract that quotes someone or a length in inches.
          if (pendingDelimiters.size() == 1
              && (previousNonBlank == '='
                  || previousNonBlank == '#'
                  || previousNonBlank == '{'
                  || previousNonBlank == '(')) {
            inQuotedValue = true;
            quotedValueBraces = 0;
          }
        } else if (c == '{') {
          pendingDelimiters.push('}');
          started = true;
        } else if (c == '(') {
          // A parenthesis delimits only the entry itself, so it opens a delimiter only before the
          // entry's own delimiter has been seen.
          if (pendingDelimiters.isEmpty()) {
            pendingDelimiters.push(')');
            started = true;
          } else if (inParenEntryTopLevel()) {
            // Remember the parenthesis, so that the ")" that matches it is not mistaken for the
            // delimiter that closes the entry.
            ordinaryParens++;
          }
        } else if (c == '}' || c == ')') {
          if (c == ')' && ordinaryParens > 0 && inParenEntryTopLevel()) {
            ordinaryParens--;
          } else {
            Character innermost = pendingDelimiters.peek();
            if (innermost != null && innermost == c) {
              pendingDelimiters.pop();
              if (pendingDelimiters.isEmpty()) {
                return;
              }
            }
          }
        }
        if (c != ' ' && c != '\t') {
          previousNonBlank = c;
        }
      }
    }
  }
}
