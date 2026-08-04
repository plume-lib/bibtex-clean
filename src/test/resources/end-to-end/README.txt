This directory holds the data for the end-to-end tests, which are run by
src/test/java/org/plumelib/bibtex/BibtexCleanEndToEndTest.java.

Each subdirectory of this directory is one test case; the name of the subdirectory is the name of
the test case.  A test case runs the program once, in a fresh working directory, and compares
everything the program produced to the goal files.  To add a test case, add a subdirectory; no Java
code needs to change.

A test case directory may contain the following, all of them optional:

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

  goal-out.txt         What the program should write to standard output.  If this file is absent,
                       the program should write nothing to standard output.

  goal-err.txt         What the program should write to standard error.  If neither this file nor
                       goal-err-prefix.txt is present, the program should write nothing to standard
                       error.

  goal-err-prefix.txt  What the program's standard error should start with.  Use this file instead
                       of goal-err.txt when the rest of the output would make the goal file
                       brittle, as a stack trace's line numbers would.

  goal-status.txt      The program's exit status.  If this file is absent, the exit status should
                       be 0.

The test harness compares text, not bytes: it treats CRLF and LF as the same line separator,
because the program ends each line it writes with the line separator of the platform it runs on.
It also uncompresses any file whose name ends with ".gz" before comparing it, because compressed
bytes depend on the JDK's compression implementation.

Because these files are test data, their exact bytes matter: some of them intentionally have
trailing whitespace, lack a final line separator, or use CRLF line separators.  prek.toml excludes
this directory from the hooks that would "fix" such things.
