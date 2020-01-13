import csv

starmap = {}

source = open('db.csv').read().split('\n')[1:]
for stars in csv.reader(source):
    name = stars[0]
    id = name_id(name)
    size = int(stars[1])
    grants = tuple(map(int, stars[2:7]))
    costs = tuple(map(int, stars[8:13]))
    crossroad = (size, costs, grants)
    starmap[name] = crossroad


for name, stars in starmap.items():
    size, costs, grants = stars
    costs = f'{str(costs)[1:-1]}'
    grants = f'{str(grants)[1:-1]}'
    key = f'"{name}"'
    value = f'{size}, {{{costs}}}, {{{grants}}}'
    print(f'{{{key}, {{{value}}}}},')
