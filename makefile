builddir:
	mkdir -p build
emsdk:
	source emsdk/emsdk_env.sh
wasm: builddir emsdk
	em++ starnav.cpp -o build/starnav.js \
	-std=c++2a -O2 -fno-exceptions \
        -s WASM_OBJECT_FILES=0 --llvm-lto 1 \
	--bind -s MODULARIZE=1 -s 'EXPORT_NAME="wasm"' \
	-s EXPORTED_FUNCTIONS='["_main"]' \
	-s EXTRA_EXPORTED_RUNTIME_METHODS='["ccall", "cwrap"]' 
clang:
	clang++-10 starnav.cpp -o build/starnav \
	-std=c++2a  \
	-O3 -flto -fuse-ld=lld-10
debug:
	clang++-10 starnav.cpp -o build/starnav \
	-std=c++2a  \
	-fsanitize=address,undefined -g -fno-omit-frame-pointer
