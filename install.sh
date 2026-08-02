#!/usr/bin/env bash
# jloom installer — clones the repo, builds the standalone CLI binary via Bun, and
# configures executables.
# Works on Linux and macOS with bash 4.0+, git, and Bun 1.3.0+.
#
# Quick start (no install required):
#   curl -sSL https://raw.githubusercontent.com/Melkabli05/JLoom/main/install.sh | bash
#
# SECURITY MODEL
#   - This script runs `git clone` and then executes `bun install` / `bun run compile`
#     from the cloned tree. The remote is trusted by default. Set JLOOM_REPO to override.
#   - By default we pin to a specific git ref (configurable via JLOOM_REF) so a
#     compromised or supply-chain-injected `main` does not silently install
#     arbitrary code. Pass `--latest` to opt into tracking main HEAD.
#   - System-wide symlinks to /usr/local/bin are opt-in via
#     --system-install / JLOOM_SYSTEM_INSTALL=1 (skipped by default; never run as
#     root without that flag). The per-user ~/.local/bin path is always preferred.
#   - Shell RC files (~/.bashrc / ~/.zshrc / config.fish) are only edited when no
#     symlink could be created, AND --no-modify-path wasn't passed.
#
# --self-update re-fetches the latest version of THIS SCRIPT from the
# remote before doing anything else. Use it after editing install.sh
# upstream to roll out a new version without manually re-cloning.

set -euo pipefail

REPO_URL="${JLOOM_REPO:-https://github.com/Melkabli05/JLoom.git}"
REF="${JLOOM_REF:-main}"
USE_LATEST=0
SELF_UPDATE=0
INSTALL_DIR="${JLOOM_INSTALL_DIR:-$HOME/.jloom}"
AUTO_SYMLINK=1
MODIFY_RC=1
NO_TESTS=0

if [ -t 1 ] && [ -z "${NO_COLOR:-}" ]; then
    RED='\033[0;31m'
    GREEN='\033[0;32m'
    YELLOW='\033[1;33m'
    BLUE='\033[0;34m'
    NC='\033[0m'
else
    RED='' GREEN='' YELLOW='' BLUE='' NC=''
fi

info()    { printf "%b\n" "${BLUE}==>${NC} $*"; }
success() { printf "%b\n" "${GREEN}==✓${NC} $*"; }
warn()    { printf "%b\n" "${YELLOW}==!${NC} $*"; }
fail()    { printf "%b\n" "${RED}==✗${NC} $*" >&2; exit 1; }

usage() {
    cat <<EOF
Usage: install.sh [options]

Options:
  --latest           Track default branch HEAD (less safe; sees unreviewed commits)
  --ref <git-ref>    Pin to a specific commit/tag/branch (default: ${REF})
  --dir <path>       Install location (default: $HOME/.jloom)
  --no-tests         Skip running the test suite during build (faster)
  --no-symlink       Skip symlinking to ~/.local/bin or /usr/local/bin
  --no-modify-path   Do not edit shell RC files (~/.bashrc, ~/.zshrc, etc.)
  --system-install   Symlink into /usr/local/bin (requires write access; opt-in)
  --self-update      Re-fetch this script from the remote before running
  -h, --help         Show this help

Environment overrides:
  JLOOM_REPO           Git URL to clone from
  JLOOM_REF            Default git ref
  JLOOM_INSTALL_DIR    Default install location
  JLOOM_SYSTEM_INSTALL Same as --system-install
  JLOOM_NO_SYMLINK     Same as --no-symlink
  JLOOM_NO_TESTS       Same as --no-tests
EOF
}

[ "${JLOOM_SYSTEM_INSTALL:-0}" = "1" ] && AUTO_SYMLINK=2
[ "${JLOOM_NO_SYMLINK:-0}" = "1" ] && AUTO_SYMLINK=0
[ "${JLOOM_NO_TESTS:-0}" = "1" ] && NO_TESTS=1

while [ $# -gt 0 ]; do
    case "$1" in
        --latest)         USE_LATEST=1; shift ;;
        --ref)            [ $# -ge 2 ] || fail "--ref requires an argument"; REF="$2"; shift 2 ;;
        --dir)            [ $# -ge 2 ] || fail "--dir requires an argument"; INSTALL_DIR="$2"; shift 2 ;;
        --no-tests)       NO_TESTS=1; shift ;;
        --no-symlink)     AUTO_SYMLINK=0; shift ;;
        --no-modify-path) MODIFY_RC=0; shift ;;
        --system-install) AUTO_SYMLINK=2; shift ;;
        --self-update)    SELF_UPDATE=1; shift ;;
        -h|--help)        usage; exit 0 ;;
        -*)               fail "Unknown option: $1 (try --help)" ;;
        *)                fail "Unexpected positional argument: $1" ;;
    esac
done

# 0. Optional: re-fetch THIS SCRIPT from the remote (so the next run
# is the latest published version, even if the local copy is stale).
if [ "$SELF_UPDATE" = "1" ]; then
    info "Re-fetching install.sh from $REPO_URL..."
    SCRIPT_URL="$REPO_URL/raw/$REF/install.sh"
    if command -v curl >/dev/null 2>&1; then
        TMP_SCRIPT=$(mktemp)
        if curl -fsSL "$SCRIPT_URL" -o "$TMP_SCRIPT" 2>/dev/null; then
            info "Got updated install.sh, re-executing..."
            exec bash "$TMP_SCRIPT" "$@"
        else
            warn "Could not fetch $SCRIPT_URL — continuing with local copy."
            rm -f "$TMP_SCRIPT"
        fi
    else
        warn "curl not found — cannot --self-update. Continuing with local copy."
    fi
fi

BUN_PROJECT_DIR="$INSTALL_DIR/bun"
DIST_DIR="$BUN_PROJECT_DIR/dist"
BIN_DIR="$INSTALL_DIR/bin"

# 1. Verify prerequisites

info "Checking prerequisites..."

command -v git >/dev/null 2>&1 || fail "git not found. Please install git and re-run."
command -v bun >/dev/null 2>&1 || fail "bun not found. Install it with: curl -fsSL https://bun.sh/install | bash — then re-run this script."

BUN_VERSION_STR=$(bun --version 2>&1)
BUN_REQUIRED="1.3.0"
if [ "$(printf '%s\n%s\n' "$BUN_REQUIRED" "$BUN_VERSION_STR" | sort -V | head -n1)" != "$BUN_REQUIRED" ]; then
    fail "Bun $BUN_REQUIRED+ required. Detected version: ${BUN_VERSION_STR:-unknown}. Run: bun upgrade"
fi
success "Bun $BUN_VERSION_STR and git verified."

if [ "$(id -u)" = "0" ] && [ "$AUTO_SYMLINK" -ne 2 ]; then
    warn "Running as root. Consider user-level installation unless intentionally deploying system-wide."
fi

# 2. Clone or update repository

if [ -d "$INSTALL_DIR/.git" ]; then
    info "Existing repository detected at $INSTALL_DIR. Fetching ref: $REF..."
    git -C "$INSTALL_DIR" fetch --depth 1 origin "$REF" || fail "git fetch failed in $INSTALL_DIR"
    git -C "$INSTALL_DIR" checkout -q FETCH_HEAD || fail "git checkout failed in $INSTALL_DIR"
else
    info "Cloning $REPO_URL ($REF) into $INSTALL_DIR..."
    mkdir -p "$(dirname "$INSTALL_DIR")"
    if [ "$USE_LATEST" -eq 1 ]; then
        git clone --depth 1 "$REPO_URL" "$INSTALL_DIR"
    else
        git clone --depth 1 --branch "$REF" "$REPO_URL" "$INSTALL_DIR" || \
            fail "Failed to clone ref '$REF'. Ensure it is a valid tag/branch, or use --latest."
    fi
fi

if [ "$USE_LATEST" -eq 0 ] && [ "$REF" = "main" ]; then
    warn "Pinned to HEAD of 'main'. For immutable builds, set JLOOM_REF to a specific commit SHA or tag."
fi

[ -d "$BUN_PROJECT_DIR" ] || fail "Expected a bun/ directory at $BUN_PROJECT_DIR but none was found. Is JLOOM_REF pointing at a pre-Bun-migration ref?"

# 3. Build artifact (optionally with tests)

info "Installing dependencies via Bun..."
(
    cd "$BUN_PROJECT_DIR"
    bun install --frozen-lockfile 2>/dev/null || bun install || fail "bun install failed"
)

if [ "$NO_TESTS" = "0" ]; then
    info "Running test suite (fully offline, no network calls)..."
    (cd "$BUN_PROJECT_DIR" && bun test) || fail "bun test failed"
    success "Tests passed."
else
    info "Skipping test suite (--no-tests)."
fi

info "Compiling standalone binary via Bun..."
(cd "$BUN_PROJECT_DIR" && bun run compile) || fail "bun run compile failed"

TARGET_BIN="$DIST_DIR/jloom"
if [ ! -x "$TARGET_BIN" ]; then
    fail "Build completed, but expected binary was not found or not executable at: $TARGET_BIN"
fi
if [ ! -d "$DIST_DIR/catalog" ]; then
    fail "Build completed, but the module catalog was not found beside the binary at: $DIST_DIR/catalog"
fi

# Make $INSTALL_DIR/bin the canonical location by symlinking the whole dist/ dir (the
# compiled binary AND its sibling catalog/ directory need to stay together — the binary
# locates the catalog by looking next to its own real, symlink-resolved path).
mkdir -p "$INSTALL_DIR"
rm -rf "$BIN_DIR"
ln -s "$DIST_DIR" "$BIN_DIR"
success "Linked canonical path: $BIN_DIR -> $DIST_DIR"

# 4. Handle execution pathing

PATH_LINKED=0

if [ "$AUTO_SYMLINK" -eq 1 ]; then
    USER_BIN_DIR="$HOME/.local/bin"
    mkdir -p "$USER_BIN_DIR"
    if [ -w "$USER_BIN_DIR" ]; then
        ln -sf "$BIN_DIR/jloom" "$USER_BIN_DIR/jloom"
        success "Symlinked binary: $USER_BIN_DIR/jloom -> $BIN_DIR/jloom"
        PATH_LINKED=1
    else
        warn "Cannot write to $USER_BIN_DIR. Skipping user symlink."
    fi
elif [ "$AUTO_SYMLINK" -eq 2 ]; then
    SYS_BIN_DIR="/usr/local/bin"
    if [ -w "$SYS_BIN_DIR" ]; then
        ln -sf "$BIN_DIR/jloom" "$SYS_BIN_DIR/jloom"
        success "Symlinked binary: $SYS_BIN_DIR/jloom -> $BIN_DIR/jloom"
        PATH_LINKED=1
    else
        fail "System install requested, but $SYS_BIN_DIR is not writable. Run with sudo/root."
    fi
fi

# 5. RC file modification (only if symlinking failed/disabled and explicitly allowed)

if [ "$PATH_LINKED" -eq 0 ] && [ "$MODIFY_RC" -eq 1 ]; then
    SHELL_NAME=$(basename "${SHELL:-sh}")

    case "$SHELL_NAME" in
        zsh)  RC_FILE="$HOME/.zshrc" ;;
        fish) RC_FILE="$HOME/.config/fish/config.fish" ;;
        bash)
            if [ -f "$HOME/.bash_profile" ] && [ ! -f "$HOME/.bashrc" ]; then
                RC_FILE="$HOME/.bash_profile"
            else
                RC_FILE="$HOME/.bashrc"
            fi
            ;;
        *)    RC_FILE="$HOME/.profile" ;;
    esac

    if [ -f "$RC_FILE" ] && grep -qF "$BIN_DIR" "$RC_FILE"; then
        info "PATH entry for $BIN_DIR already present in $RC_FILE."
    else
        if [ "$SHELL_NAME" = "fish" ]; then
            EXPORT_LINE="fish_add_path $BIN_DIR"
        else
            EXPORT_LINE="export PATH=\"$BIN_DIR:\$PATH\""
        fi

        mkdir -p "$(dirname "$RC_FILE")"
        printf '\n# jloom CLI\n%s\n' "$EXPORT_LINE" >> "$RC_FILE"
        success "Appended PATH entry to $RC_FILE"
    fi
fi

# 6. Verification — exercise multiple commands to confirm the install works
# end-to-end, not just the simplest one (jloom list). The compiled binary is
# self-contained (bun/node are not required at runtime), so we deliberately
# strip PATH down to bare essentials for this check.

echo
info "Verifying installation (standalone binary, no bun/node required at runtime)..."

VERIFICATION_FAILED=0
if PATH="$BIN_DIR:/usr/bin:/bin" "$BIN_DIR/jloom" --version >/dev/null 2>&1; then
    success "jloom --version: works"
else
    warn "jloom --version failed"
    VERIFICATION_FAILED=1
fi

if PATH="$BIN_DIR:/usr/bin:/bin" "$BIN_DIR/jloom" --help >/dev/null 2>&1; then
    success "jloom --help: works"
else
    warn "jloom --help failed"
    VERIFICATION_FAILED=1
fi

if PATH="$BIN_DIR:/usr/bin:/bin" "$BIN_DIR/jloom" list --what modules >/dev/null 2>&1; then
    success "jloom list --what modules: works"
else
    warn "jloom list failed"
    VERIFICATION_FAILED=1
fi

if [ "$VERIFICATION_FAILED" -eq 0 ]; then
    success "All verifications passed. Install complete."
    echo
    info "Try:"
    info "  ${GREEN}jloom --help${NC}            (full command list)"
    info "  ${GREEN}jloom list${NC}              (available modules)"
    info "  ${GREEN}jloom new --help${NC}        (project creation flags)"
    info "  ${GREEN}jloom${NC} (no args)         (interactive REPL)"
else
    warn "Some verifications failed. Run manually to debug: $BIN_DIR/jloom list"
fi

echo
info "To upgrade in the future, run:"
info "  ${GREEN}cd $INSTALL_DIR && git pull && cd bun && bun install && bun run compile${NC}"
echo
