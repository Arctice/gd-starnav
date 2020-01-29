builddir:
	mkdir -p build
clang:
	clang++-10 starnav.cpp -o build/starnav \
	-std=c++2a \
	-O2
perf:
	clang++-10 starnav.cpp -o build/starnav \
	-std=c++2a \
	-O1 -g -fno-omit-frame-pointer
debug:
	clang++-10 starnav.cpp -o build/starnav \
	-std=c++2a  \
	-fsanitize=address,undefined -g -fno-omit-frame-pointer \
        -Wall -Wextra -Werror -Wpedantic \
        -Wunreachable-code -Wuninitialized -Waddress -Wtautological-compare \
        -Wtautological-unsigned-zero-compare -Woverloaded-virtual
wasm: builddir
	em++ starnav.cpp -o build/starnav.js \
	-std=c++2a -O2 -fno-exceptions \
        -s ALLOW_MEMORY_GROWTH=1 \
        -s WASM_OBJECT_FILES=0 --llvm-lto 1 --closure 1 \
	--bind -s MODULARIZE=1 -s 'EXPORT_NAME="wasm"' \
	-s EXPORTED_FUNCTIONS='["_main"]'
resources:
	cp build/starnav.* starnav/resources/public && \
	cp worker.js starnav/resources/public

