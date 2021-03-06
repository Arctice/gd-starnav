#include <iostream>
#include <iomanip>
#include <vector>
#include <unordered_map>
#include <array>
#include <bitset>
#include <unordered_set>
#include <tuple>
#include <optional>
#include <numeric>
#include <algorithm>
#include <limits>
#include <memory>
#include <queue>
#include "hashmap/phmap.h"

#include "starnav.h"


std::ostream& operator<<(std::ostream& ss, const affinity& v)
{
    ss << '(';
    for (std::size_t n(0); n < 4; ++n) ss << (int)v[n] << ", ";
    return ss << (int)v[4] << ')';
}

template <typename Fn>
affinity vec_op(Fn transform, const affinity& lhs, const affinity& rhs)
{
    affinity val;
    for (int n(0); n < 5; ++n) { val[n] = transform(lhs[n], rhs[n]); }
    return val;
}

using starset = std::bitset<128>;
using starid = i8;

std::unordered_map<std::string, starset> name_id_map;
std::unordered_map<starset, std::string> id_name_map;
std::unordered_map<starset, starid> compact_id;

starset name_id(std::string name)
{
    if (name_id_map.count(name)) return name_id_map.at(name);
    auto bit = name_id_map.size();
    name_id_map[name].set(bit);
    id_name_map[name_id_map[name]] = name;
    compact_id[name_id_map[name]] = bit;
    return name_id_map[name];
}

starset decompactize(starid bit)
{
    auto id = starset{};
    id.set(bit);
    return id;
}

std::string str_name(starset id) { return id_name_map.at(id); }

using stardata = std::pair<starset, constellation>;

auto starmap = std::vector<stardata>{};

void init()
{
    for (const auto& [name, stars] : stardb) {
        auto position = compact_id[name_id(name)];
        starmap.resize(std::max<std::size_t>(starmap.size(), position + 1));
        starmap[position] = {name_id(name), stars};
    }
}

struct devotion {
    starset chosen{};
    i8 points{55};
    affinity affinity{};

    bool operator==(const devotion& rhs) const
    {
        return chosen == rhs.chosen && points == rhs.points;
    }

    bool is_chosen(const starset& id) const { return (chosen & id).any(); }
    bool is_unchosen(const stardata& stars) const
    {
        return stars.second.size <= points and not is_chosen(stars.first);
    }
    bool is_open(const constellation& stars) const
    {
        auto diff = vec_op(std::minus<i8>{}, affinity, stars.cost);
        bool affinity = std::all_of(diff.begin(), diff.end(),
                                    [](i8 v) { return v >= 0; });
        bool points = stars.size <= this->points;
        return points && affinity;
    }
    bool is_available(const stardata& stars) const
    {
        return not is_chosen(stars.first) and is_open(stars.second);
    }

    bool is_removable(const stardata& stars) const
    {
        if (not is_chosen(stars.first)) return false;
        if (not remove(stars).is_available(stars)) return false;

        auto h = remove(stars);
        auto m = h.missing_affinity(h.affinity_cost());
        for (int n(0); n < 5; ++n) {
            if (m[n]) return false;
        }

        return true;
    }

    devotion add(const stardata& stars) const
    {
        i8 points_ = points - stars.second.size;
        auto chosen_ = chosen | stars.first;
        auto affinity_ = vec_op(std::plus<i8>{}, affinity, stars.second.gain);
        return devotion{chosen_, points_, affinity_};
    };

    devotion remove(const stardata& stars) const
    {
        i8 points_ = points + stars.second.size;
        auto chosen_ = chosen ^ stars.first;
        auto affinity_ = vec_op(std::minus<i8>{}, affinity, stars.second.gain);
        return devotion{chosen_, points_, affinity_};
    };

    ::affinity affinity_cost() const
    {
        ::affinity total{};
        for (const auto& [id, stars] : starmap) {
            if (not is_chosen(id)) continue;
            total = vec_op([](const auto& lhs,
                              const auto& rhs) { return std::max(lhs, rhs); },
                           total, stars.cost);
        }
        return total;
    }

    ::affinity missing_affinity(::affinity cost) const
    {
        cost = vec_op(std::minus<i8>{}, cost, affinity);
        auto missing = vec_op(
            [](const auto& lhs, const auto& rhs) { return std::max(lhs, rhs); },
            ::affinity{}, cost);
        return missing;
    }
};

struct hash_starset {
    auto operator()(const starset& v) const
    {
#ifndef __EMSCRIPTEN__
        return std::hash<starset>{}(v);
#else
        static const starset lower_bits{
            std::numeric_limits<unsigned long long>::max()};
        auto l = ((v & lower_bits)).to_ullong();
        auto u = (v >> 64).to_ullong();
        return std::hash<unsigned long long>{}(l)
               ^ std::hash<unsigned long long>{}(u);
#endif
    }
};

namespace std{
template <> struct hash<devotion> {
    auto operator()(const devotion& v) const { return hash_starset{}(v.chosen); }
};
    
};

struct set_interface {
    std::unordered_set<starset, hash_starset> set;
    void add(const starset& v) { set.insert(v); }
    bool query(const starset& v) const { return set.count(v); }
};

using visited = set_interface;

devotion incomplete_state(int max_devotion,
                          const std::vector<std::string>& constraints)
{
    devotion state;
    state.points = max_devotion;
    for (const auto& name : constraints) {
        auto id = name_id(name);
        auto stars = starmap.at(compact_id[id]);
        state.chosen |= stars.first;
        state.points -= stars.second.size;
        state.affinity = vec_op(std::plus<i8>{}, state.affinity, stars.second.gain);
    }

    return state;
}

auto cmp_first
    = [](auto& lhs, auto& rhs) { return std::get<0>(lhs) < std::get<0>(rhs); };
std::size_t max_queue_size = 50000;

short tension(devotion state)
{
    auto total_tension = 0;
    for (const auto& stars : starmap) {
        const auto& [id, data] = stars;
        if (not state.is_chosen(id)) continue;
        if (state.is_removable(stars)) continue;
        auto support = vec_op(std::minus<i8>{}, state.affinity, data.gain);
        auto tension
            = vec_op([](const auto& lhs,
                        const auto& rhs) { return std::max<i8>(0, lhs - rhs); },
                     data.cost, support);
        total_tension += std::accumulate(tension.begin(), tension.end(), 0);
    }
    return total_tension;
}

bool reachable(int max_devotion, devotion start)
{
    std::vector<std::tuple<short, short, devotion>> queue{
        {start.points, 0, start}};
    visited seen;
    seen.add(start.chosen);

    while (!queue.empty()) {
        std::pop_heap(queue.begin(), queue.end(), cmp_first);
        auto [points, path, node] = queue.back();
        queue.pop_back();

        if (node.points == max_devotion) { return true; }

        for (const auto& stars : starmap) {
            auto next = node;
            if (node.is_available(stars)) { next = node.add(stars); }
            else if (node.is_removable(stars))
                next = node.remove(stars);
            else
                continue;
            if (seen.query(next.chosen)) continue;
            seen.add(next.chosen);

            int heuristic = next.points - (path + 1) - tension(next);
            queue.push_back({heuristic, path + 1, next});
            std::push_heap(queue.begin(), queue.end(), cmp_first);
        }

        if (queue.size() > max_queue_size) {
            queue.erase(queue.begin() + max_queue_size / 2, queue.end());
        }
    }
    return false;
}

i8 affinity_heuristic(const devotion& state, const affinity& cost)
{
    auto affinity = state.missing_affinity(cost);
    return -std::accumulate(affinity.begin(), affinity.end(), (i8)0);
}

struct search_item{
    i8 heuristic;
    affinity cached_cost;
    devotion state;

    bool operator<(const search_item& rhs) const
    {
        return heuristic < rhs.heuristic;
    };
};

std::optional<devotion>
possible_completion(int max_devotion,
                    const std::vector<std::string>& constraints,
                    int search_depth)
{
    auto incomplete = incomplete_state(max_devotion, constraints);
    if (incomplete.points < 0) return {};
    auto affinity_cost = incomplete.affinity_cost();

    std::priority_queue<search_item, std::vector<search_item>> queue{};
    queue.push({affinity_heuristic(incomplete, affinity_cost), affinity_cost,
                incomplete});

    phmap::flat_hash_set<starset, hash_starset> seen;
    seen.insert(incomplete.chosen);

    auto threshold_steps{4 + std::max(0, search_depth)};
    auto threshold{103682ll};
    auto processed{0ll};

    while (!queue.empty()) {
        auto [cost, affinity_cost, node] = queue.top();
        queue.pop();

        processed++;
        if(processed > threshold){
            threshold = threshold * 1.618034;
            threshold_steps--;
            if (threshold_steps < 0) return {};
        }

        if (cost == 0 and reachable(max_devotion, node)) { return node; }

        for (const auto& stars : starmap) {
            if (not node.is_unchosen(stars)) continue;

            auto next = node.add(stars);
            auto next_affinity_cost
                = vec_op([](const auto& lhs,
                            const auto& rhs) { return std::max(lhs, rhs); },
                         affinity_cost, stars.second.cost);
            i8 next_cost = affinity_heuristic(next, next_affinity_cost);

            if (next_cost <= cost) continue;
            if (seen.contains(next.chosen)) continue;

            seen.insert(next.chosen);
            queue.push({next_cost, next_affinity_cost, next});
        }
    }

    return {};
};

template <typename T> struct persistent_list_node {
    std::size_t len{0};
    std::shared_ptr<const persistent_list_node> head{nullptr};
    T value;

    std::size_t size() const { return len; }
};

template <typename T>
using persistent_list = std::shared_ptr<const persistent_list_node<T>>;

template <typename T> persistent_list<T> cons(const T& v, persistent_list<T> l)
{
    auto len = l ? l->len + 1 : 1;
    return persistent_list<T>{new persistent_list_node<T>{len, l, v}};
}

enum class change : i8 { cut, add };
struct action {
    change step;
    starid stars;
};

std::optional<persistent_list<action>> reach(int max_devotion, devotion start)
{
    std::vector<std::tuple<short, devotion, persistent_list<action>>> queue{
        {start.points, start, {}}};
    visited seen;
    seen.add(start.chosen);

    while (!queue.empty()) {
        std::pop_heap(queue.begin(), queue.end(), cmp_first);
        auto [points, node, path] = queue.back();
        queue.pop_back();

        if (node.points == max_devotion) { return {path}; }

        for (const auto& stars : starmap) {
            auto next = node;
            auto step{change::add};
            if (node.is_available(stars)) { next = node.add(stars); }
            else if (node.is_removable(stars)) {
                next = node.remove(stars);
                step = change::cut;
            }
            else
                continue;
            if (seen.query(next.chosen)) continue;
            seen.add(next.chosen);

            auto new_path = cons({step, compact_id.at(stars.first)}, path);
            int heuristic = next.points - new_path->size() - tension(next);
            queue.push_back({heuristic, next, new_path});
            std::push_heap(queue.begin(), queue.end(), cmp_first);
        }

        if (queue.size() > max_queue_size) {
            queue.erase(queue.begin() + max_queue_size / 2, queue.end());
        }
    }

    return {};
}

void print_choices(const devotion& state)
{
    for (const auto& [id, name] : id_name_map) {
        if (state.is_chosen(id)) std::cout << name << std::endl;
    }
}

template <typename S, typename V> bool contains(const S& collection, const V& v)
{
    return std::find(collection.begin(), collection.end(), v)
           != collection.end();
}

template <typename K, typename V> class cache {
    std::unordered_map<K, std::pair<V, short>> data;
    std::vector<K> random_access;
    unsigned short access_counter{};
    std::size_t max_load;

    void evict()
    {
        auto a = std::rand() % random_access.size();
        auto b = std::rand() % random_access.size();
        auto least_recent = data.at(random_access[a]).second
                                    <= data.at(random_access[b]).second
                                ? a
                                : b;

        data.erase(random_access[least_recent]);
        std::swap(random_access[least_recent], random_access.back());
        random_access.pop_back();
    }

public:
    cache() : max_load(8 * 1024 / sizeof(V)) { std::srand(max_load); }

    bool contains(const K& key) const { return data.find(key) != data.end(); };

    void store(const K& key, const V& val)
    {
        if (data.size() > max_load) evict();

        if (!contains(key)) random_access.push_back(key);
        data[key] = {val, access_counter++};
    }

    const V& load(const K& key)
    {
        auto& storage = data.at(key);
        storage.second = access_counter++;
        return storage.first;
    }
};

cache<devotion, starset> solution_cache;

std::vector<std::string> unpack_stars(const starset& set){
    std::vector<std::string> out;
    for (const auto& stars : starmap) {
        const auto& [id, data] = stars;
        auto name = str_name(id);
        if ((set & id).any()) out.push_back(name);
    }
    return out;
}

std::vector<std::string> possible_choices(int max_devotion,
                                          std::vector<std::string> constraints,
                                          int search_depth)
{
    auto reference_state = incomplete_state(max_devotion, constraints);
    if (solution_cache.contains(reference_state))
        return unpack_stars(solution_cache.load(reference_state));

    auto results = std::vector<std::string>{};

    for (const auto& stars : starmap) {
        const auto& [id, data] = stars;
        auto name = str_name(id);
        if (contains(constraints, name)) continue;
        if (reference_state.points < data.size) continue;
        constraints.push_back(name);

        auto accessible
            = possible_completion(max_devotion, constraints, search_depth);
        if (accessible) { results.push_back(name); }
        constraints.pop_back();
    }

    solution_cache.store(reference_state,
                         incomplete_state(max_devotion, results).chosen);

    return results;
}

class module_initializer {
public:
    module_initializer()
    {
        std::cerr << std::setprecision(2);
        init();
    }
} _;

struct devotion_step {
    bool is_add;
    std::string starname;
};

std::vector<devotion_step> solve_path(int max_devotion,
                                      std::vector<std::string> constraints,
                                      int search_depth)
{
    auto viable_state
        = possible_completion(max_devotion, constraints, search_depth);
    if (not viable_state) return {};

    auto search = reach(max_devotion, *viable_state);
    if (not search) return {};

    std::vector<devotion_step> result{};

    auto path = *search;
    while (path) {
        bool is_add = path->value.step == change::add;
        auto name = str_name(decompactize(path->value.stars));
        result.push_back({is_add, name});
        path = path->head;
    }

    return result;
}

std::vector<std::string>
solve(int max_devotion, std::vector<std::string> constraints, int search_depth)
{
    auto solution = possible_choices(max_devotion, constraints, search_depth);
    return solution;
}

bool valid_choice(int max_devotion, std::vector<std::string> constraints,
                  std::string new_star, int search_depth)
{
    auto reference_state = incomplete_state(max_devotion, constraints);
    auto results = std::vector<std::string>{};

    const auto& [id, data] = starmap.at(compact_id.at(name_id(new_star)));
    if (contains(constraints, new_star)) return false;
    if (reference_state.points < data.size) return false;

    constraints.push_back(new_star);
    auto accessible
        = possible_completion(max_devotion, constraints, search_depth);
    return (bool)accessible;
}

std::vector<std::string> find_path(int max_devotion,
                                   std::vector<std::string> constraints,
                                   int search_depth)
{
    if (not possible_completion(max_devotion, constraints, search_depth))
        return {{"Impossible constellations!"}};

    auto solution = solve_path(max_devotion, constraints, search_depth);

    std::vector<std::string> path_steps;
    for (const auto& [is_add, star] : solution) {
        std::string step = std::string(is_add ? "cut" : "add") + ": ";
        step += star;
        path_steps.push_back(step);
    }

    return path_steps;
}

extern "C" int main()
{
#ifndef __EMSCRIPTEN__
    std::vector<std::string> constraints{
        "Alladrah\'s Phoenix",
        "Gallows",
        "Hyrian, Guardian of the Celestial Gates",
        "Revenant",
        "Scales of Ulcama",
        "Tempest",
        "Eel",
        // "Vire, the Stone Matron",
        // "Yugol, the Insatiable Night",
    };

    // possible_choices(55, constraints);
    // std::cerr << (bool)possible_completion(55, constraints, 2) << "\n";
#endif
}


#ifdef __EMSCRIPTEN__

#include <emscripten/bind.h>

using namespace emscripten;

EMSCRIPTEN_BINDINGS(starnav)
{
    register_vector<std::string>("strvec");
    function("solve", &solve);
    function("valid_choice", &valid_choice);
    function("find_path", &find_path);
}

#endif
