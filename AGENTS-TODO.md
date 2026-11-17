# Plan: improve the end-to-end tests of bibtex-clean

**This temporary plan file must be deleted before any pull request is merged.**

Goal: improve the end-to-end test suite of plume-lib/bibtex-clean, in the
worktree `/home/mernst/java/plume-lib/bibtex-clean-fork-mernst-branch-e2e-tests`
on branch `e2e-tests`.  The tests must be data in files (inputs, arguments,
goal stdout/stderr/exit status/output files), so that they can be run against
another implementation, in another language.  Extend the existing format in
`src/test/resources/end-to-end/` (documented in its `README.txt`, run by
`src/test/java/org/plumelib/bibtex/BibtexCleanEndToEndTest.java`); do not
rename existing files.  Each test case has a `description.txt`; a description
that starts with "BUG:" documents a bug, and its goal files record current
behavior.  Do not change the program's main source.  Convert Java tests that
hard-code input and expected output into the file-based format, and delete the
old form.  Commit at each milestone (including this file); never push, create
PRs, or rewrite history.  `./gradlew build` must pass at the end.  When every
entry is done, delete this file and commit the deletion.

## Checklist

- [x] Harness: normalize CRLF in stdout and stderr too (README already promises it).
- [x] Harness: `-Pe2e.command=...` Gradle property / `e2e.command` system
      property to run another implementation (README.txt still to document it).
- [x] Harness: pass the JaCoCo agent to the child JVM, so coverage still
      counts once BibtexCleanTest is deleted.
- [ ] README.txt: spell out the contract for an alternate implementation
      (working directory, arguments, environment, comparisons, normalization).
- [x] Fix stale "should be removed, but is not" lines in existing inputs.
- [x] Convert BibtexCleanTest cases (6 new cases) and delete BibtexCleanTest.java.
- [x] Add 27 new cases (goals recorded from current behavior and reviewed).
- [ ] `./gradlew spotlessApply build` passes; markdownlint passes on this file.
- [ ] Delete this file and commit.
