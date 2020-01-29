#!/bin/bash
set -e

source emsdk/emsdk_env.sh
make wasm resources

cd starnav
lein cljsbuild once
