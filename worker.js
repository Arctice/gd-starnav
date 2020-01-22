self.importScripts("starnav.js");

var module;
var threadid;

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
    var solution = module.solve(points, starvec);
    var result = collect(solution);

    message("result", result, token);
}

function solve_one(args, token){
    var points = args[0];
    var state = args[1];
    var choice = args[2];

    var starvec = buildvec(state);
    var solution = module.valid_choice(points, starvec, choice);

    message("result", solution, token);
}


onmessage = function(msg){
    var dispatch = msg.data[0];
    var data = msg.data[1];
    var token = msg.data[2];

    if(dispatch == "init"){ return init(data); }
    if(!ready()){ return message(false); }
    if(dispatch == "solve"){ return solve(data, token); }
    if(dispatch == "solve-one"){ return solve_one(data, token); }

    console.log(msg.data);
    message("???");
}
