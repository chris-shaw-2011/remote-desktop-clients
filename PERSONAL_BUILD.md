# Personal aRDP build automation

This fork builds the existing non-free `aRDP-app` module from the repository's
GPL source. It does not change licensing or feature checks. The resulting
package is verified as `com.iiordanov.aRDP`, and only the signed ARM64 APK is
published.

## Workflows

- `.github/workflows/build-ardp.yml` runs on pushes to `master`, can be run
  manually, and is reusable by the sync workflow. It downloads the repository's
  prebuilt native dependencies, runs the documented project preparation step,
  applies the repository's Java-side FreeRDP glyph-cache compatibility patch when
  dependency archive 17 lacks it, builds `:aRDP-app:assembleRelease`, signs and
  verifies the ARM64 split, uploads it as a 30-day Actions artifact, and creates
  a uniquely tagged GitHub Release.
- `.github/workflows/sync-upstream.yml` runs every six hours and on demand. It
  fetches `upstream/master`, does nothing when that commit is already contained
  in the fork's default branch, and otherwise creates and pushes a normal merge
  commit. It never force-pushes. After a successful push, it calls the reusable
  build workflow directly, so no PAT is needed to work around GitHub's
  `GITHUB_TOKEN` workflow-trigger restriction.

The workflows assume that this fork's default branch remains `master`, matching
upstream. If the branch is renamed, update the `push.branches` entry in
`build-ardp.yml`; the sync workflow discovers the fork's default branch at run
time.

## Multiple Quest panels

Every saved connection, shortcut, `rdp://` link, and `.rdp` file opens in its
own resizable Android task. Selecting a saved connection replaces the connection
grid inside its current panel, so the RDP session inherits that panel's position
and size instead of appearing beside it. Reopen aRDP to create a new connection
grid panel, position that picker where the next session should remain, and
select another connection. Closing or disconnecting one RDP panel affects only
that session.

Shortcuts, `rdp://` links, and `.rdp` files have no existing picker panel to
replace, so they continue to open a new RDP panel. Saved connections that invoke
an external VPN client also retain the separate-panel flow.

aRDP does not impose a connection limit. The number of panels that remain live
depends on Horizon OS window limits and available memory. Force-stopping aRDP or
having Android terminate its process ends all sessions; the personal build does
not add a foreground service. Device clipboard synchronization follows the
focused panel, while audio and microphone redirection remain configured per
connection.

## One-time GitHub setup

In **Settings > Actions > General**:

1. Enable GitHub Actions for the repository.
2. Under **Workflow permissions**, select **Read and write permissions**.
3. Leave Actions allowed to create releases and push to `master`. If a branch
   protection rule or ruleset blocks direct pushes, allow `github-actions[bot]`
   to bypass that rule (or otherwise permit this workflow's merge commits).

In **Settings > Secrets and variables > Actions**, create these repository
secrets:

| Secret | Contents |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | The complete signing keystore, base64-encoded as one string |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Alias passed to `keytool` |
| `ANDROID_KEY_PASSWORD` | Private-key password |

Do not commit the keystore or any of these values.

## Create a signing keystore

Install a current JDK, choose strong unique passwords, and run this once in a
private location outside the repository:

```bash
keytool -genkeypair -v \
  -keystore ardp-personal-release.jks \
  -alias ardp-personal \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

Set `ANDROID_KEY_ALIAS` to `ardp-personal`. The command prompts for the
keystore/private-key passwords and certificate identity. Keep the original
keystore and passwords in a secure backup; they are required for every update.

On Linux, produce the value for `ANDROID_KEYSTORE_BASE64` with:

```bash
base64 -w 0 ardp-personal-release.jks
```

On macOS, where `base64` does not support `-w`, use:

```bash
base64 < ardp-personal-release.jks | tr -d '\n'
```

On Windows PowerShell, use:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("ardp-personal-release.jks"))
```

Copy only the resulting base64 text into the GitHub secret.

## Run and download builds

Any push to `master` starts **Build personal aRDP APK**. To build manually, open
the repository's **Actions** tab, select that workflow, choose **Run workflow**,
and run it from `master`. The signed APK is available both from the run's
**Artifacts** section and from the repository's **Releases** page. A manual run
from a non-default branch still uploads an artifact but intentionally does not
create a permanent Release.

The **Sync upstream** workflow can also be started with **Run workflow**. Its
scheduled runs occur at minute 17 every sixth hour (UTC). A sync-created push is
built by the reusable workflow within the same run.

## Recover from an upstream conflict

If the automatic merge conflicts, the workflow fails without pushing. Resolve
the merge locally with normal Git commands, review and test the result, then
push the resolved merge to `master`:

```bash
git remote add upstream https://github.com/iiordanov/remote-desktop-clients.git
git fetch upstream master
git switch master
git merge --no-ff upstream/master
# Resolve the files reported by Git, then:
git add <resolved-files>
git commit
git push origin master
```

If an `upstream` remote already exists, use `git remote set-url upstream ...`
instead of `git remote add`. The push starts the normal aRDP build workflow.

## Signing-key rotation

Replace all four signing secrets together when rotating the key, and retain a
secure backup of the old key. Android will not install an APK signed by a new
key as an update to an installation signed by the old key. Key rotation
therefore requires uninstalling the old app (which can remove its local app
data) before installing the newly signed build.
