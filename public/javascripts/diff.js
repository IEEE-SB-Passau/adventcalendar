function diffNodes(nodeA, nodeB) {
    if (nodeA.firstChild == null || nodeB.firstChild == null) {
        return;
    }

    var linesA = nodeA.firstChild.data.split(/\r?\n/);
    var linesB = nodeB.firstChild.data.split(/\r?\n/);

    var result = [];
    for (var i = 0; i < linesB.length; i++) {
        if (i === linesB.length - 1 && linesB[i].length === 0) {
            // skip adding last, empty line because it's just a newline we added before
        } else if (linesA[i] === linesB[i]) {
            result = result.concat(document.createTextNode(linesB[i] + "\n"));
        } else {
            var wrapper = document.createElement("span");
            wrapper.setAttribute("class", "diff-mismatch");
            wrapper.appendChild(document.createTextNode(linesB[i]));
            result = result.concat(wrapper);
            result = result.concat(document.createTextNode("\n"));
        }
    }

    nodeB.removeChild(nodeB.firstChild);
    for (var j = 0; j < result.length; j++) {
        nodeB.appendChild(result[j]);
    }
}

function applyDiff() {
    var expected = $(".expected-output").get();
    var actual = $(".actual-output").get();
    var count = Math.min(expected.length, actual.length);

    for (var i = 0; i < count; i++) {
        diffNodes(expected[i], actual[i]);
    }
}
