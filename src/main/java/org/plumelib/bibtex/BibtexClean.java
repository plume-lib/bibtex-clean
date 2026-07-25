package org.plumelib.bibtex;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.regex.Pattern;
import org.plumelib.util.EntryReader;
import org.plumelib.util.FilesP;

/**
 * Clean a BibTeX file by removing text outside BibTeX entries.
 *
 * <p>Remove each non-empty line that is not in a BibTeX entry, except retain any line that starts
 * with "%". Each BibTeX entry should not contain blank lines. {@code @string} entries should fit on
 * one line.
 *
 * <p>Arguments are the names of the original files. Cleaned copies of those files are written in
 * the CURRENT DIRECTORY. Therefore, this should be run in a different directory from where the
 * argument files are, to avoid overwriting them.
 */

// The implementation uses regular expressions rather than a BibTeX parser,
// because BibTeX parsers generally do not preserve formatting, such as
// indentation, delimiter characters, and order of fields.  And, the ones I
// looked at were not very well documented.

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

  /** Regex for the end of a BibTeX entry. */
  private static final Pattern entryEnd =
      Pattern.compile(
          "^[ \t]*"
              + ("("
                  + "[a-z0-9_]+[ \t]*=[ \t]*"
                  + ("("
                      + "\\{[^{}]*\\}|\".*\"|"
                      + "[12][0-9][0-9][0-9]|Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec"
                      + ")")
                  + ",?[ \t]*"
                  + ")*")
              + "[)}]",
          Pattern.CASE_INSENSITIVE);

  /** Regex for a BibTeX string definition. */
  private static final Pattern stringDef =
      Pattern.compile("^@string(\\{.*\\}|\\(.*\\))$", Pattern.CASE_INSENSITIVE);

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
      // Delete the file to work around a bug.  Files.newBufferedWriter (which is called by
      // FilesP.newBufferedFileWriter) seems to have a bug where it does not correctly truncate the
      // file first.  If the target file already exists, then characters beyond what is written
      // remain in the file.
      outFile.delete();
      // `bw`, rather than the PrintWriter that wraps it, is the resource, so that an IOException
      // thrown while closing the file is propagated rather than being suppressed by PrintWriter.
      try (BufferedWriter bw = FilesP.newBufferedFileWriter(outFile.toString());
          EntryReader er = new EntryReader(filename)) {
        PrintWriter out = new PrintWriter(bw);
        clean(er, out, System.err);
        // PrintWriter suppresses IOException, so ask it whether writing succeeded.  `checkError`
        // flushes `out`, so this accounts for everything that `clean` wrote.
        if (out.checkError()) {
          System.err.printf("Problem writing %s%n", outFile);
          System.exit(2);
        }
      } catch (IOException e) {
        System.err.printf(
            "Problem reading %s or writing %s: %s%n", inFile, outFile, e.getMessage());
        System.exit(2);
      }
    }
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
      } else if (line.startsWith("@")) {
        out.println(line);
        if (!stringDef.matcher(line).matches()) {
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
            if (entryEnd.matcher(line2).lookingAt()) {
              break;
            }
          }
        }
      }
    }
  }
}
