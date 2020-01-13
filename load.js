
function main(module){
    console.log(module._mul(3, 4));
}

require('./build/starnav.js')().then(main);
