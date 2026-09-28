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
  public void closingBraceOfFieldValueDoesNotEndEntry() throws IOException {
    // The "}," line closes the brace that the abstract opened, not the one that the entry opened,
    // so the entry continues.
    String input =
        lines(
            "@article{k,",
            "  abstract = {A long abstract",
            "    that continues on another line",
            "  },",
            "  year = 2020",
            "}",
            "trailing junk that must be dropped");
    String expected =
        lines(
            "@article{k,",
            "  abstract = {A long abstract",
            "    that continues on another line",
            "  },",
            "  year = 2020",
            "}");
    CleanResult result = clean(input);
    assertEquals(expected, result.out());
    assertEquals("", result.err());
  }

  @Test
  public void keepsIndentedEntry() throws IOException {
    // BibTeX permits whitespace before the "@".
    String input = lines("  @article{k,", "  year = 2020", "}", "noise");
    String expected = lines("  @article{k,", "  year = 2020", "}");
    assertEquals(expected, cleaned(input));
  }

  @Test
  public void singleLineEntryIsComplete() throws IOException {
    // The entry's value is quoted and contains nested braces, none of which ends the entry early.
    String input = lines("@preamble{\"\\newcommand{\\noop}[1]{}\"}", "noise");
    String expected = lines("@preamble{\"\\newcommand{\\noop}[1]{}\"}");
    assertEquals(expected, cleaned(input));
  }

  @Test
  public void keepsStringDefinitionWithTrailingSpace() throws IOException {
    String input = lines("@string{pub = \"Publisher\"} ", "more noise");
    String expected = lines("@string{pub = \"Publisher\"} ");
    assertEquals(expected, cleaned(input));
  }

  @Test
  public void endsEntryAfterAbbreviationFieldValue() throws IOException {
    String input = lines("@article{k,", "  journal = jacm}", "noise");
    String expected = lines("@article{k,", "  journal = jacm}");
    CleanResult result = clean(input);
    assertEquals(expected, result.out());
    assertEquals("", result.err());
  }

  @Test
  public void unmatchedParenInBracedValueDoesNotEndEntry() throws IOException {
    String input = lines("@article{k,", "  note = {A smiley :-)},", "  year = 2020", "}", "noise");
    String expected = lines("@article{k,", "  note = {A smiley :-)},", "  year = 2020", "}");
    assertEquals(expected, cleaned(input));
  }

  @Test
  public void atLineWithNoDelimiterIsAnEntryByItself() throws IOException {
    String input = lines("@ this line is not an entry", "noise");
    String expected = lines("@ this line is not an entry");
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

  @Test
  public void unmatchedOpenParenInBracedValueDoesNotEndEntry() throws IOException {
    // BibTeX gives parentheses no meaning within an entry, so the "(" does not open a delimiter
    // that the entry would then await forever.
    String input = lines("@article{k,", "  note = {A frown :-(},", "  year = 2020", "}", "noise");
    String expected = lines("@article{k,", "  note = {A frown :-(},", "  year = 2020", "}");
    CleanResult result = clean(input);
    assertEquals(expected, result.out());
    assertEquals("", result.err());
  }

  @Test
  public void unmatchedOpenParenInQuotedValueDoesNotEndEntry() throws IOException {
    String input =
        lines(
            "@article{k,",
            "  title = \"Proceedings of Foo (1999\",",
            "  year = 2020",
            "}",
            "noise");
    String expected =
        lines("@article{k,", "  title = \"Proceedings of Foo (1999\",", "  year = 2020", "}");
    CleanResult result = clean(input);
    assertEquals(expected, result.out());
    assertEquals("", result.err());
  }

  @Test
  public void closingBraceInQuotedValueDoesNotEndEntry() throws IOException {
    // The "}" is within a quoted field value, so it is ordinary text rather than the delimiter
    // that closes the entry.
    String input =
        lines(
            "@article{k,",
            "  title = \"A closing } brace in a quoted value\",",
            "  year = 2020",
            "}",
            "noise");
    String expected =
        lines(
            "@article{k,",
            "  title = \"A closing } brace in a quoted value\",",
            "  year = 2020",
            "}");
    CleanResult result = clean(input);
    assertEquals(expected, result.out());
    assertEquals("", result.err());
  }

  @Test
  public void quotationMarkInBracedValueIsOrdinaryText() throws IOException {
    // The quotation mark is within braces, so it does not start a quoted field value that would
    // hide the delimiters after it.
    String input =
        lines("@article{k,", "  abstract = {He said \"hi},", "  year = 2020", "}", "noise");
    String expected = lines("@article{k,", "  abstract = {He said \"hi},", "  year = 2020", "}");
    CleanResult result = clean(input);
    assertEquals(expected, result.out());
    assertEquals("", result.err());
  }

  @Test
  public void openingDelimiterOnNextLine() throws IOException {
    // BibTeX permits whitespace, including a line separator, between the entry type and the
    // entry's opening delimiter, so the entry does not end after the "@" line.
    String input = lines("@article", "{k,", "  year = 2020", "}", "noise");
    String expected = lines("@article", "{k,", "  year = 2020", "}");
    CleanResult result = clean(input);
    assertEquals(expected, result.out());
    assertEquals("", result.err());
  }

  @Test
  public void openingDelimiterOnNextLineAfterEntryTypeWithDigit() throws IOException {
    // An entry type may contain more than letters.
    String input = lines("@misc2", "{k,", "  year = 2020", "}", "noise");
    String expected = lines("@misc2", "{k,", "  year = 2020", "}");
    CleanResult result = clean(input);
    assertEquals(expected, result.out());
    assertEquals("", result.err());
  }

  @Test
  public void atLineThatIsOrdinaryTextIsAnEntryByItself() throws IOException {
    // A line of text that starts with an email address is not an entry type followed by an entry,
    // so it does not start a search for a delimiter on a later line.
    String input = lines("@example.com", "noise");
    String expected = lines("@example.com");
    CleanResult result = clean(input);
    assertEquals(expected, result.out());
    assertEquals("", result.err());
  }

  @Test
  public void matchedParenInParenDelimitedEntryDoesNotEndEntry() throws IOException {
    // The ")" matches the "(" that precedes it, which is ordinary text, so it is ordinary text
    // rather than the delimiter that closes the entry.
    String input =
        lines("@article(k,", "  note = see (Smith 1999),", "  year = 2020", ")", "noise");
    String expected = lines("@article(k,", "  note = see (Smith 1999),", "  year = 2020", ")");
    CleanResult result = clean(input);
    assertEquals(expected, result.out());
    assertEquals("", result.err());
  }

  @Test
  public void quotationMarkThatStartsNoValueIsOrdinaryText() throws IOException {
    // The quotation mark does not follow "=", "#", or the entry's opening delimiter, so it does
    // not start a quoted field value that would hide the delimiters after it.
    String input = lines("@misc{k,", "  note = 5\" floppy disk,", "  year = 2020", "}", "noise");
    String expected = lines("@misc{k,", "  note = 5\" floppy disk,", "  year = 2020", "}");
    CleanResult result = clean(input);
    assertEquals(expected, result.out());
    assertEquals("", result.err());
  }

  @Test
  public void backslashDoesNotHideClosingDelimiter() throws IOException {
    // BibTeX counts braces without regard to backslashes, so the "}" closes the value's brace even
    // though a backslash precedes it.
    String input = lines("@misc{k,", "  title = {A path C:\\},", "  year = 2020", "}", "noise");
    String expected = lines("@misc{k,", "  title = {A path C:\\},", "  year = 2020", "}");
    CleanResult result = clean(input);
    assertEquals(expected, result.out());
    assertEquals("", result.err());
  }

  @Test
  public void quotationMarkWithinBracesDoesNotEndQuotedValue() throws IOException {
    // The quotation mark of the umlaut accent is within braces, so it is ordinary text rather than
    // the end of the quoted value.  If it ended the value, then the "}" just after it would look
    // like the delimiter that closes the entry, and the rest of the entry would be discarded.
    String input =
        lines(
            "@article{k,", "  author = \"Schl{\\\"o}mer, Thomas\",", "  year = 2020", "}", "noise");
    String expected =
        lines("@article{k,", "  author = \"Schl{\\\"o}mer, Thomas\",", "  year = 2020", "}");
    CleanResult result = clean(input);
    assertEquals(expected, result.out());
    assertEquals("", result.err());
  }

  @Test
  public void quotedValueStartingOnNextLine() throws IOException {
    // The "=" that shows that a quoted field value follows is on the previous line.
    String input =
        lines("@misc{k,", "  title =", "    \"A } brace\",", "  year = 2020", "}", "noise");
    String expected = lines("@misc{k,", "  title =", "    \"A } brace\",", "  year = 2020", "}");
    CleanResult result = clean(input);
    assertEquals(expected, result.out());
    assertEquals("", result.err());
  }
}
