This directory holds the data for the end-to-end tests, which are run by
src/test/java/org/plumelib/bibtex/BibtexCleanEndToEndTest.java.

The test data is independent of the implementation of the program.  The same test cases can check
another implementation of bibtex-clean, written in any language, either by passing its command to
the Java test harness (see "Running the tests against another implementation", below) or by a test
harness written in another language that follows the rules in this file.


Test case layout
================

Each subdirectory of this directory is one test case; the name of the subdirectory is the name of
the test case.  A test case runs the program once, in a fresh working directory, and compares
everything the program produced to the goal files.  To add a test case, add a subdirectory; no Java
code needs to change.

A test case directory contains the following, all of them optional.  A test case directory must not
contain any other file; the test harness reports an unrecognized file, which is probably a typo in a
goal file's name.

  description.txt      What the test case checks.  The test harness ignores this file.  A
                       description that starts with "BUG:" describes a way in which the program
                       behaves differently than it should; the goal files record what the program
                       currently does.

  input/               Files to clean.  The test harness copies this directory into the working
                       directory, so the program is not run in the directory that holds its input
                       files.  Unless args.txt exists, the program's arguments are every file in
                       this directory, in sorted order, named as "input/...".

  initial/             Files that already exist in the working directory when the program runs.
                       The test harness copies them into the working directory itself, not into
                       "input/".

  args.txt             The program's command-line arguments, one per line.  Use this file when the
                       arguments are not simply the files of "input/": for example, to name a file
                       that does not exist, to name a file twice, or to run the program with no
                       arguments at all (an empty args.txt).

  goal/                The files that should be in the working directory after the program runs.
                       The "input/" directory that the test harness created is ignored, but
                       everything else must match, in both name and contents, so a test case also
                       detects a file that the program should not have created.  If this directory
                       is absent, the working directory should contain no files.

  unchecked-files.txt  Names of files in the working directory, one per line, that the comparison
                       with goal/ ignores:  after the program runs, each such file is permitted to
                       exist, with any contents, or not to exist.  Use this file when whether the
                       program leaves a file behind depends on the implementation, as when the
                       program fails partway through writing an output file.  A file named here
                       must not also be in goal/.

  goal-out.txt         What the program should write to standard output.  If this file is absent,
                       the program should write nothing to standard output.

  goal-err.txt         What the program should write to standard error.  If neither this file nor
                       goal-err-prefix.txt is present, the program should write nothing to standard
                       error.

  goal-err-prefix.txt  What the program's standard error should be, except for the end of its last
                       line.  Use this file instead of goal-err.txt when the end of a diagnostic
                       would make the goal file brittle, as an operating system's or a library's
                       description of an error would.  Such a description differs among
                       implementations and platforms, so it is not part of what the program must
                       write.

  goal-status.txt      The program's exit status, as a decimal integer.  If this file is absent,
                       the exit status should be 0.


How a test harness runs a test case
===================================

A test harness, in any language, runs a test case as follows.

 1. Create a new, empty working directory.

 2. If the test case has an "input/" directory, copy it, recursively, to "input/" in the working
    directory.  If the test case has an "initial/" directory, copy its contents, recursively, into
    the working directory itself.  Copy bytes exactly; do not translate line separators.

 3. Determine the arguments.  If the test case has args.txt, read it as UTF-8; each non-empty line
    is one argument, used verbatim (an argument can contain spaces, and no shell quoting or
    expansion is applied).  Otherwise, the arguments are the names of all regular files under the
    working directory's "input/" directory, each written relative to the working directory with "/"
    as the separator (as in "input/refs.bib" or "input/a/refs.bib"), in sorted order (by
    Unicode code point).  If there is no "input/" directory, there are no arguments.

 4. Run the program with the working directory as its current directory, with those arguments, and
    with the environment that the test harness itself has.  The program does not read standard
    input.  Capture the program's standard output and standard error in files outside the working
    directory, so that they are not mistaken for files that the program wrote.  Wait for the
    program to exit; the Java test harness gives up after 120 seconds.

 5. Compare the results, as described in the next section.  The test case passes if every
    comparison succeeds.

The program is invoked as
  bibtex-clean ARG...
There are no command-line options; every argument is the name of a file to clean.  For example,
the Java test harness runs
  java -cp CLASSPATH org.plumelib.bibtex.BibtexClean input/refs.bib


Comparisons and normalization
=============================

The test harness compares the following.

 * Exit status:  equal to the integer in goal-status.txt, or 0.

 * Standard output:  equal to goal-out.txt, or empty.

 * Standard error:  equal to goal-err.txt; or, if goal-err-prefix.txt exists, equal to its contents
   with trailing whitespace (including the final line separator) removed, followed by the rest of
   that last line:  any characters other than a line separator, then one line separator; or empty.

 * Files:  the set of regular files under the working directory, excluding everything under
   "input/", is the same as the set of regular files under goal/ (compared by relative path); and
   each such file's contents equal the contents of the corresponding goal file.  Directories are not
   compared, except as they contain files.

All comparisons are of text, decoded as UTF-8, after this normalization:

 * Every CRLF line separator is replaced by LF, in both the goal and the actual text, because the
   program ends each line it writes, including each line of standard output and standard error,
   with the line separator of the platform it runs on.  A lone CR is not changed.

 * A file whose name ends with ".gz" is uncompressed (gzip) before it is compared, because
   compressed bytes depend on the compression implementation.  The program must write such a file
   in gzip format.

No other normalization is applied.  In particular, trailing whitespace, empty lines, and the
presence or absence of a final line separator are significant.

Because these files are test data, their exact bytes matter: some of them intentionally have
trailing whitespace, lack a final line separator, use CR or CRLF line separators, contain bytes
that are not valid UTF-8, or start with a byte order mark.  prek.toml excludes this directory from
the hooks that would "fix" such things, and .gitattributes tells git not to translate them.


Running the tests against another implementation
================================================

By default, the Java test harness runs this implementation, in a new JVM, for each test case.  To
run the test cases against another implementation, give the command that runs it:

  ./gradlew test --tests '*EndToEnd*' -Pe2e.command="/usr/local/bin/bibtex-clean"
  ./gradlew test --tests '*EndToEnd*' -Pe2e.command="python3 /home/me/bibtex_clean.py"

The Gradle property e2e.command becomes the system property e2e.command of the JVM that runs the
tests (which can also be set directly, as in an IDE).  The test harness splits its value at
whitespace and appends each test case's arguments.  Because the program runs in the test case's
working directory, any file name in the command, including the executable's, should be absolute,
unless the executable is found on the PATH.


What the program does
=====================

An implementation passes the tests if it behaves as follows.  The test cases are the definitive
specification; this summary is a guide to them.

 * For each argument, in order, the program cleans the named file (the input) into a file in the
   current directory whose name is the last component of the argument (the output), as in
   "input/sub/refs.bib" -> "refs.bib".  A later output silently replaces an earlier one of the same
   name.  An existing output file is replaced completely.

 * If the input and the output are the same file (as when the argument is "refs.bib" or
   "./refs.bib"), the program writes
     Input file ARG is also the output file; run in a different directory.
   to standard error and exits with status 2, without changing the file.

 * If the input cannot be opened (as when it does not exist, or is not valid gzip), the program
   writes a line that starts with
     Problem reading ARG or writing OUTPUT:
   to standard error and exits with status 2, without creating or changing the output file.  The
   same message, with status 2, reports a failure while reading or writing; then the output file
   has already been created.  The program stops at the first such failure, so later arguments are
   not cleaned.  With no arguments, the program does nothing and exits with status 0.

 * A file whose name ends with ".gz" is read and written in gzip format.  Input is decoded as UTF-8
   (with each invalid byte replaced by U+FFFD), and output is encoded as UTF-8.  CR, LF, and CRLF
   all end a line, and every line written, including the last, ends with a line separator.

 * The output contains, verbatim and in order, the lines of the input that are empty, that start
   with "%", or that are part of an entry.  An entry starts at a line whose first non-blank
   character (blank meaning space or tab) is "@".  It ends at the line on which its opening "{" or
   "(" is closed (delimiters being counted as described in the test cases), or else at the next
   empty line or at end of input; in those two cases the program writes
     ARG:LINE: unterminated entry: TEXT
     ARG:LINE: unterminated entry at EOF: TEXT
   to standard error, where LINE is the 1-based line number of the "@" line and TEXT is that line,
   and continues; such a diagnostic does not change the exit status.  An "@" line with no opening
   delimiter is an entry by itself, unless it consists of only an entry type (a letter followed by
   letters, digits, "-", and "_", with optional surrounding blanks), in which case the opening
   delimiter is sought on later lines.  The program writes nothing to standard output.
