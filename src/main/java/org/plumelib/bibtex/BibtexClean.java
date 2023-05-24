package org.plumelib.bibtex;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.regex.Pattern;
import org.plumelib.util.EntryReader;
import org.plumelib.util.UtilPlume;

/**
 * Clean a BibTeX file by removing text outside BibTeX entries.
 *
 * <p>Remove each non-empty line that is not in a BibTeX entry, except retain any line that starts
 * with "%".
 *
 * <p>Arguments are the names of the original files. Cleaned copies of those files are written in
 * the CURRENT DIRECTORY. Therefore, this should be run in a different directory from where the
 * argument files are, to avoid overwriting them.
 */

// The implementation uses regular expressions rather than a BibTeX parser,
// because BibTeX parsers generally do not preserve formatting, such as
// indentation, delimiter characters, and order of fields.  And, the ones I
// looked at were not very well-documented.

// The implementation cannot use EntryReader to iterate through the file
// because the @ line does not necessarily follow a blank line -- there
// might be a comment line before it.  But, EntryReader requires that its
// "long entries" start after a blank line.  (That can be considered an
// EntryReader bug, or at least inflexibility in its interface.)

@SuppressWarnings("deprecation") // uses deprecated classes in this package
public final class BibtexClean {

  /** This class is a collection of methods; it does not represent anything. */
  private BibtexClean() {
    throw new Error("do not instantiate");
  }

  /** Regex for the end of a BibTeX entry. */
  private static Pattern entry_end =
      Pattern.compile("^[ \t]*(?i)(year[ \t]*=[ \t]*[12][0-9][0-9][0-9][ \t]*)?[)}]");

  /** Regex for a BibTeX string definition. */
  private static Pattern stringDef = Pattern.compile("^@(?i)string(\\{.*\\}|\\(.*\\))$");

  /**
   * Main method for the BibtexClean program.
   *
   * @param args command-line arguments
   */
  public static void main(String[] args) {
    for (String filename : args) {
      File inFile = new File(filename);
      File outFile = new File(inFile.getName()); // in current directory
      // Delete the file to work around a bug.  Files.newBufferedWriter (which is called by
      // UtilPlume.bufferedFileWriter) seems to have a bug where it does not correctly truncate the
      // file first.  If the target file already exists, then characters beyond what is written
      // remain in the file.
      outFile.delete();
      try (PrintWriter out = new PrintWriter(UtilPlume.bufferedFileWriter(outFile.toString()));
          EntryReader er = new EntryReader(filename)) {
        for (String line : er) {
          if (line.equals("") || line.startsWith("%")) {
            out.println(line);
          } else if (line.startsWith("@")) {
            if (stringDef.matcher(line).matches()) {
              out.println(line);
            } else {
              out.println(line);
              while (er.hasNext() && ((line = er.next()) != null)) {
                out.println(line);
                if (entry_end.matcher(line).lookingAt()) {
                  break;
                } else if (line.equals("")) {
                  System.err.printf(
                      "%s:%d: unterminated entry%n", er.getFileName(), er.getLineNumber());
                  break;
                }
              }
            }
          }
        }
      } catch (IOException e) {
        System.err.printf(
            "Problem reading %s + or writing %s: %s", inFile, outFile, e.getMessage());
        System.exit(2);
      }
    }
  }
}
