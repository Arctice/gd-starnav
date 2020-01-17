self.importScripts("starnav.js");

var module;

function ready(){ return module !== undefined; }

function message(msg, data, token){
    postMessage({"msg": msg,
                 "result": data,
                 "token": token});
}

function init(){
    if(ready()) return;
    wasm().then(function(wasm){ module = wasm; message("ready"); });
}

function collect(vector){
    var arr = [];
    for (var i = 0; i < vector.size(); ++i) { arr[i] = vector.get(i); }
    return arr;
}

function solve(args, token){
    var points = args[0];
    var stars = args[1];

    var starvec = new module.strvec;
    for (var i in stars) {
        starvec.push_back(stars[i]);
    }
    var solution = module.solve(points, starvec);
    var result = collect(solution);

    message("result", result, token);
}

onmessage = function(msg){
    var dispatch = msg.data[0];
    var data = msg.data[1];
    var token = msg.data[2];

    if(dispatch == "init"){ return init(); }
    if(!ready()){ return message(false); }
    if(dispatch == "solve"){ return solve(data, token); }
    message("???");
}
