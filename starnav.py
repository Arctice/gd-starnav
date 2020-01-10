from collections import namedtuple, deque
from itertools import chain
import heapq
import time
import csv

affinity = namedtuple('affinity', ('P', 'R', 'G', 'Y', 'B'))


def affinity_add(base, modifier):
    return affinity(*(base[i] + modifier[i] for i in range(5)))


def affinity_remove(base, modifier):
    return affinity(*(base[i] - modifier[i] for i in range(5)))


def affinity_max(base, modifier):
    return affinity(*(max(base[i], modifier[i]) for i in range(5)))


affinity.add = affinity_add
affinity.remove = affinity_remove
affinity.max = affinity_max

constellation = namedtuple('constellation', ('size', 'cost', 'reward'))


def empty_affinity():
    return affinity(0, 0, 0, 0, 0)


def name_id(name):
    if name not in name_id.order:
        name_id.order.append(name)
    return 0x1 << name_id.order.index(name)


def str_name(id):
    return name_id.order[id.bit_length() - 1]


name_id.order = []

starmap = {}

for crossroad in range(5):
    name = name_id(affinity._fields[crossroad])
    size = 1
    grants = [0] * 5
    grants[crossroad] = 1
    grants = affinity(*grants)
    costs = affinity(*[0] * 5)
    crossroad = constellation(size, costs, grants)
    starmap[name] = crossroad

source = open('db.csv').read().split('\n')[1:]
for stars in csv.reader(source):
    name = name_id(stars[0])
    size = int(stars[1])
    grants = affinity(*map(int, stars[2:7]))
    costs = affinity(*map(int, stars[8:13]))
    crossroad = constellation(size, costs, grants)
    starmap[name] = crossroad

state = namedtuple('state', ('chosen', 'devotion', 'affinity'))


def is_chosen(self, name):
    return self.chosen & name


def is_open(self, stars):
    return (stars.size <= self.devotion and all(
        (self.affinity[A] >= stars.cost[A] for A in range(5))))


def available(self):
    for name, stars in starmap.items():
        if not self.is_chosen(name) and self.is_open(stars):
            yield name


def removable(self):
    for name in starmap:
        if not self.is_chosen(name):
            continue
        if not name in self.valid_remove(name).available():
            continue
        yield name


def add(self, name):
    assert not self.is_chosen(name), 'already chosen'
    stars = starmap[name]
    assert self.is_open(stars), 'invalid constellation choice'
    devotion = self.devotion - stars.size
    chosen = self.chosen | name
    affinity = self.affinity.add(stars.reward)
    return state(chosen, devotion, affinity)


def remove(self, name):
    assert self.is_chosen(name), 'can\'t remove an unchosen constellation'
    stars = starmap[name]
    devotion = self.devotion + stars.size
    chosen = self.chosen ^ name
    affinity = self.affinity.remove(stars.reward)
    return state(chosen, devotion, affinity)


def statehash(self):
    return self.chosen


state.is_chosen = is_chosen
state.is_open = is_open
state.available = available
state.removable = removable
state.valid_add = add
state.valid_remove = remove
state.__hash__ = statehash


def affinity_cost(state):
    total = empty_affinity()
    for name, stars in starmap.items():
        if not state.is_chosen(name):
            continue
        total = total.max(stars.cost)
    return total


def missing_affinity(state):
    cost = state.affinity_cost()
    return cost.remove(state.affinity).max(empty_affinity())


def is_valid(self):
    return sum(self.missing_affinity()) == 0


def unchosen(self):
    for name, stars in starmap.items():
        if not self.is_chosen(name) and stars.size <= self.devotion:
            yield name


def wishful_add(self, name):
    assert not self.is_chosen(name), 'already chosen'
    stars = starmap[name]
    assert self.devotion >= stars.size, 'not enough devotion'
    devotion = self.devotion - stars.size
    chosen = self.chosen | name
    affinity = self.affinity.add(stars.reward)
    return state(chosen, devotion, affinity)


state.affinity_cost = affinity_cost
state.missing_affinity = missing_affinity
state.unchosen = unchosen
state.wishful_add = wishful_add
state.is_valid = is_valid


def incomplete_state(constraints):
    constraints = list(constraints)
    assert len(constraints) == len(
        set(constraints)), 'constraints contain duplicates'

    devotion = 55
    affinity = empty_affinity()
    chosen = 0
    for name in constraints:
        stars = starmap[name]
        chosen |= name
        devotion -= stars.size
        affinity = affinity.add(stars.reward)

    assert devotion >= 0, 'devotion cost of constraints is too high'
    return state(chosen, devotion, affinity)


def affinity_heuristic(state):
    return sum(state.missing_affinity())


def valid_states(constraints):
    initial = incomplete_state(constraints)
    queue = [(affinity_heuristic(initial), initial)]
    seen = set((initial.chosen, ))

    while queue:
        cost, node = heapq.heappop(queue)
        if cost == 0:
            yield node
            continue

        adds = (node.wishful_add(stars) for stars in node.unchosen())
        new = tuple(state for state in adds if state.chosen not in seen)
        seen.update((state.chosen for state in new))
        for next in new:
            cost = affinity_heuristic(next)
            heapq.heappush(queue, (cost, next))

    node = initial
    adds = (node.wishful_add(stars) for stars in node.unchosen())


def reach(state):
    queue = [(55 - state.devotion, state, ())]
    seen = set((state.chosen, ))

    processed = 0
    while queue:
        spent, node, path = heapq.heappop(queue)

        processed += 1
        if node.devotion == 55:
            print(processed)
            return path

        adds = ((('cut', stars), node.valid_add(stars))
                for stars in node.available())
        cuts = ((('add', stars), node.valid_remove(stars))
                for stars in node.removable())
        new = (state for state in chain(adds, cuts)
               if state[1].chosen not in seen)
        new = tuple(new)
        seen.update((state[1].chosen for state in new))
        for step, next in new:
            new_path = path + (step, )
            heuristic = 55 - next.devotion + len(new_path)
            heapq.heappush(queue, (heuristic, next, new_path))
    return False


def possible(constraints):
    for result in filter(reach, valid_states(constraints)):
        return result


def possible_choices(constraints):
    assert possible(constraints), 'given constraints unsatisfiable'
    reference_state = incomplete_state(constraints)
    results = []
    for name in starmap:
        if name in constraints:
            continue
        if reference_state.devotion < starmap[name].size:
            continue
        if possible(constraints + [name]):
            results.append(name)
    return [str_name(name) for name in results]


def recreate_path(constraints):
    state = possible(constraints)
    return reach(state)


constraints = [
    'Hyrian, Guardian of the Celestial Gates',
    'Fiend',
    'Behemoth',
    'Chariot of the Dead',
    'Messenger of War',
    'Alladrah\'s Phoenix',
    'Typhos, the Jailor of Souls',
    'Staff of Rattosh',
    'Oklaine\'s Lantern',
    'Lion',
]

constraints = list(name_id(name) for name in constraints)
b = recreate_path(constraints)
for a, c in reversed(b):
    print(a, str_name(c))
# options = possible_choices(constraints)
# for star in options: print(star)
# print(recreate_path(constraints))
