#include <iostream>
#include <vector>
#include <numeric>
#include <functional>

extern "C" int mul(int a, int b)
{
	std::vector<int> c{a, b};
	return std::accumulate(std::begin(c), std::end(c), 1,
			       [](auto a, auto b) { return a * b; });
}
