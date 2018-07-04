# bibtex-clean #

Clean a BibTeX file by removing text outside BibTeX entries.

Remove each non-empty line that is not in a BibTeX entry, except retain any line that starts
with "%".

Arguments are the names of the original files. Cleaned copies of those files are written in
the **current directory**. Therefore, this should be run in a different directory from where the
argument files are, to avoid overwriting them.
