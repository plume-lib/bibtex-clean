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

- [ ] Harness: normalize CRLF in stdout and stderr too (README already promises it).
- [ ] Harness: `-Pe2e.command=...` Gradle property / `e2e.command` system
      property to run another implementation; document it in README.txt.
- [ ] README.txt: spell out the contract for an alternate implementation
      (working directory, arguments, environment, comparisons, normalization).
- [ ] Fix stale "should be removed, but is not" lines in existing inputs.
- [ ] Convert BibtexCleanTest cases not yet covered end-to-end, then delete
      BibtexCleanTest.java:
  - [ ] smiley-in-braced-value
  - [ ] frown-in-braced-value
  - [ ] paren-in-quoted-value
  - [ ] closing-brace-in-quoted-value
  - [ ] quotation-mark-in-braced-value
  - [ ] quoted-value-on-next-line
- [ ] New cases:
  - [ ] quote-after-concatenation (`#`)
  - [ ] quote-after-opening-delimiter (braces and parentheses)
  - [ ] type-only-line-with-whitespace (tab before `@`, trailing blanks)
  - [ ] type-only-line-then-blank-line (diagnostic)
  - [ ] type-only-line-at-eof (diagnostic)
  - [ ] type-only-line-without-delimiter (text retained until blank line)
  - [ ] text-after-closing-delimiter
  - [ ] two-entries-on-one-line (expected BUG)
  - [ ] text-before-at-on-same-line (expected BUG)
  - [ ] indented-comment-outside-entry
  - [ ] comment-entry (`@comment`)
  - [ ] unmatched-paren-in-paren-entry (diagnostic)
  - [ ] unbalanced-brace-in-quoted-value (diagnostic)
  - [ ] unterminated-in-several-files
  - [ ] dotted-input-path
  - [ ] input-in-working-directory-after-success
  - [ ] overwrites-existing-output
  - [ ] missing-input-keeps-previous-output
  - [ ] file-name-with-spaces
  - [ ] malformed-gzip-input
  - [ ] invalid-utf8-input
  - [ ] utf8-byte-order-mark (expected BUG)
  - [ ] cr-line-endings
  - [ ] output-path-is-directory
  - [ ] option-like-argument
  - [ ] only-text-outside-entries
- [ ] `./gradlew spotlessApply build` passes; markdownlint passes on this file.
- [ ] Delete this file and commit.
