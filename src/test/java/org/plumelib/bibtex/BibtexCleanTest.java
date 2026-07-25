package org.plumelib.bibtex;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.plumelib.util.EntryReader;

/** Tests for {@link BibtexClean}. */
public final class BibtexCleanTest {

  /** The file name that the tests give to the BibTeX text being cleaned. */
  private static final String testFileName = "test.bib";

  /**
   * The result of running {@link BibtexClean#clean}.
   *
   * @param out the cleaned BibTeX
   * @param err the diagnostics about unterminated entries
   */
  private record CleanResult(String out, String err) {}

  /**
   * Runs {@link BibtexClean#clean} on the given input.
   *
   * @param input the BibTeX text to clean
   * @return the cleaned BibTeX text and the diagnostics
   * @throws IOException if there is a problem reading the input
   */
  private static CleanResult clean(String input) throws IOException {
    StringWriter sw = new StringWriter();
    ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
    try (EntryReader er =
            new EntryReader(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), testFileName);
        PrintWriter pw = new PrintWriter(sw);
        PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8)) {
      BibtexClean.clean(er, pw, err);
    }
    return new CleanResult(sw.toString(), errBytes.toString(StandardCharsets.UTF_8));
  }

  /**
   * Runs {@link BibtexClean#clean} on the given input and returns the cleaned output.
   *
   * @param input the BibTeX text to clean
   * @return the cleaned BibTeX text
   * @throws IOException if there is a problem reading the input
   */
  private static String cleaned(String input) throws IOException {
    return clean(input).out();
  }

  /**
   * Joins the given lines, terminating each with the platform line separator (which is what {@link
   * PrintWriter#println} emits).
   *
   * @param lines the lines to join
   * @return the joined lines, each terminated by a line separator
   */
  private static String lines(String... lines) {
    StringBuilder sb = new StringBuilder();
    for (String line : lines) {
      sb.append(line).append(System.lineSeparator());
    }
    return sb.toString();
  }

  @Test
  public void removesTextOutsideEntries() throws IOException {
    String input =
        lines(
            "Junk before the entry.",
            "@article{key,",
            "  author = {Smith},",
            "  title = {A Title},",
            "  year = 2020",
            "}",
            "Junk after the entry.");
    String expected =
        lines("@article{key,", "  author = {Smith},", "  title = {A Title},", "  year = 2020", "}");
    CleanResult result = clean(input);
    assertEquals(expected, result.out());
    assertEquals("", result.err());
  }

  @Test
  public void keepsCommentLines() throws IOException {
    String input = lines("% keep this comment", "drop this text");
    String expected = lines("% keep this comment");
    assertEquals(expected, cleaned(input));
  }

  @Test
  public void keepsBlankLinesBetweenEntries() throws IOException {
    String input = lines("@string{pub = \"Publisher\"}", "", "@string{jrnl = \"Journal\"}");
    assertEquals(input, cleaned(input));
  }

  @Test
  public void keepsSingleLineStringDefinition() throws IOException {
    String input = lines("noise", "@string{pub = \"Publisher\"}", "more noise");
    String expected = lines("@string{pub = \"Publisher\"}");
    assertEquals(expected, cleaned(input));
  }

  @Test
  public void handlesQuotedFieldValueAndParenDelimiters() throws IOException {
    String input = lines("@article(key,", "  title = \"A quoted title\",", "  month = Jan", ")");
    assertEquals(input, cleaned(input));
  }

  @Test
  public void closesEntryOnClosingBraceLine() throws IOException {
    String input = lines("@book{k,", "  title = {T}", "}", "trailing junk that must be dropped");
    String expected = lines("@book{k,", "  title = {T}", "}");
    assertEquals(expected, cleaned(input));
  }

  @Test
  public void unterminatedEntryAtBlankLine() throws IOException {
    // A blank line ends an entry.  The entry is copied out verbatim, and a diagnostic that names
    // the line on which the entry started is written to the error stream.
    String input =
        lines("Junk before the entry.", "@book{k,", "  title = {T}", "", "@book{k2,", "}");
    String expected = lines("@book{k,", "  title = {T}", "", "@book{k2,", "}");
    CleanResult result = clean(input);
    assertEquals(expected, result.out());
    assertEquals(lines(testFileName + ":2: unterminated entry: @book{k,"), result.err());
  }

  @Test
  public void unterminatedEntryAtEofDoesNotCrash() throws IOException {
    // An entry that is never closed before end of input should be copied out verbatim (with a
    // diagnostic written to the error stream), not throw an exception.
    String input = lines("@book{k,", "  title = {T}");
    CleanResult result = clean(input);
    assertEquals(input, result.out());
    assertEquals(lines(testFileName + ":1: unterminated entry at EOF: @book{k,"), result.err());
  }
}
