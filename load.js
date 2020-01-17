
function print(v){ console.log(v); }

function main(module){
    var constraints = new module.strvec;
    constraints.push_back("Alladrah's Phoenix");
    constraints.push_back("Gallows");
    constraints.push_back("Hyrian, Guardian of the Celestial Gates");
    constraints.push_back("Revenant");
    constraints.push_back("Scales of Ulcama");
    constraints.push_back("Tempest");

    var result = module.solve(55, constraints);
    print(result.size());
}

require('./build/starnav.js')().then(main);
