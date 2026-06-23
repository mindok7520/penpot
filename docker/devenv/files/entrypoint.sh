#!/usr/bin/env bash

set -e

EMSDK_QUIET=1 . /opt/emsdk/emsdk_env.sh;

target_uid=${EXTERNAL_UID:-1000}
current_uid=$(id -u penpot)

if [ "$current_uid" != "$target_uid" ]; then
    if mountpoint -q /home/penpot/penpot; then
        previous_uid="$current_uid"
        sed -i -E "s/^(penpot:x:)[0-9]+:/\1${target_uid}:/" /etc/passwd
        current_uid=$(id -u penpot)
        echo "[entrypoint.sh] penpot uid changed from $previous_uid to $current_uid for mounted workspace" >&2
    elif ! usermod -u "$target_uid" penpot; then
        echo "[entrypoint.sh] warning: unable to update penpot uid to $target_uid; continuing with uid $(id -u penpot)" >&2
    fi
fi

cp /root/.bashrc /home/penpot/.bashrc
cp /root/.vimrc /home/penpot/.vimrc
cp /root/.tmux.conf /home/penpot/.tmux.conf

# Seed SERENA_HOME with default config on first run
mkdir -p ${SERENA_HOME}
if [ ! -f "${SERENA_HOME}/serena_config.yml" ]; then
    cp /home/serena_config.yml "${SERENA_HOME}/serena_config.yml"
fi
chown -R penpot:users ${SERENA_HOME}

chown penpot:users /home/penpot
for node_modules_dir in \
    /home/penpot/penpot/frontend/node_modules \
    /home/penpot/penpot/frontend/packages/ui/node_modules \
    /home/penpot/penpot/frontend/.shadow-cljs \
    /home/penpot/penpot/plugins/libs/plugins-runtime/node_modules \
    /home/penpot/penpot/exporter/node_modules \
    /home/penpot/penpot/render-wasm/node_modules; do
    [ -d "$node_modules_dir" ] && chown penpot:users "$node_modules_dir"
done
# we need to be able to install rust-analyzer and possibly other dependencies with rustup
chown -R penpot:ubuntu /opt/rustup

rsync -ar --chown=penpot:users /opt/cargo/ /home/penpot/.cargo/

export JAVA_OPTS="-Djava.net.preferIPv4Stack=true"
export PATH="/home/penpot/.cargo/bin:$PATH"
export CARGO_HOME="/home/penpot/.cargo"

export LANG=C.UTF-8
export LC_ALL=C.UTF-8
export COLORTERM=truecolor

exec "$@"
