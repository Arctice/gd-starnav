builddir:
	mkdir -p build
emsdk:
	source emsdk/emsdk_env.sh
wasm: builddir emsdk
	em++ starnav.cpp -o build/starnav.js \
	-std=c++2a \
	--bind -s MODULARIZE=1 -s 'EXPORT_NAME="wasm"' \
	-s EXPORTED_FUNCTIONS='["_mul"]' \
	-s EXTRA_EXPORTED_RUNTIME_METHODS='["ccall", "cwrap"]'

