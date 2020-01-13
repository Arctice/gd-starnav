
function print(v){ console.log(v); }

function main_(module){
    var constraints = new module.strvec;
    constraints.push_back('Affliction');
    constraints.push_back('Lion');
    var result = module.solve(constraints);
    print(result.size());
}

require('./build/starnav.js')().then(main_);
