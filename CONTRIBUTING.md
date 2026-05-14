# Contributing

## Branch workflow

`main` is a protected integration branch:

- direct pushes are blocked
- pull requests are required
- the GitHub Actions `build` check must pass before merge

Create short-lived branches from `main` using one of these prefixes:

- `feature/<short-name>` for new behavior
- `fix/<short-name>` for bug fixes
- `refactor/<short-name>` for internal cleanup
- `docs/<short-name>` for documentation-only changes
- `test/<short-name>` for test-only changes
- `chore/<short-name>` for maintenance tasks
- `release/<short-name>` for release preparation work

Examples:

- `feature/plugin-config`
- `fix/comment-reflow`
- `refactor/formatter-session`

## Typical command-line flow

```bash
git switch main
git pull --ff-only
git switch -c feature/my-change

# edit, test, commit
mvn -B --no-transfer-progress verify
git push -u origin feature/my-change
gh pr create
```

## Pull request expectations

- keep PRs focused on a single change or theme
- run `mvn -B --no-transfer-progress verify` before pushing when practical
- update tests when formatter behavior changes
- describe any intentional behavior differences clearly in the PR body

## Solo-maintainer note

GitHub does **not** allow the author of a pull request to approve their own PR.
Because of that, this repository is configured so that:

- PRs are still required
- CI is still required
- direct pushes to `main` are still blocked
- no separate approval is required to merge your own PRs

That preserves a PR-based workflow without forcing a second account.

