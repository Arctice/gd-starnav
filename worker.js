self.importScripts("starnav.js");

var module;
var threadid;

var search_depth = 2;

function ready(){ return module !== undefined; }

function message(msg, data, token){
    postMessage({"msg": msg,
                 "result": data,
                 "token": token,
                 "thread-id": thread_id});
}

function init(data){
    thread_id = data[0];
    if(ready()) return;
    wasm().then(function(wasm){ module = wasm; message("ready"); });
}

function buildvec(arr){
    var starvec = new module.strvec;
    for (var i in arr) { starvec.push_back(arr[i]); }
    return starvec;
}

function collect(vector){
    var arr = [];
    for (var i = 0; i < vector.size(); ++i) { arr[i] = vector.get(i); }
    return arr;
}

function solve(args, token){
    var points = args[0];
    var stars = args[1];

    var starvec = buildvec(stars);
    var solution = module.solve(points, starvec, search_depth);
    var result = collect(solution);

    message("result", result, token);
}

function solve_one(args, token){
    var points = args[0];
    var state = args[1];
    var choice = args[2];

    var starvec = buildvec(state);
    var solution = module.valid_choice(points, starvec, choice, search_depth);

    message("result", solution, token);
}

function find_path(args, token){
    var points = args[0];
    var state = args[1];

    var starvec = buildvec(state);
    var solution = module.find_path(points, starvec, search_depth);
    var result = collect(solution);

    message("result", result, token);
}

function set_search_depth(args, token){
    search_depth = args[0];
}


onmessage = function(msg){
    var dispatch = msg.data[0];
    var data = msg.data[1];
    var token = msg.data[2];

    if(dispatch == "init"){ return init(data); }
    if(!ready()){ return message(false); }
    if(dispatch == "solve"){ return solve(data, token); }
    if(dispatch == "solve-one"){ return solve_one(data, token); }
    if(dispatch == "find-path"){ return find_path(data, token); }
    if(dispatch == "search-depth"){ return set_search_depth(data, token); }

    // panic
}
