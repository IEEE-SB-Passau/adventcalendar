function diffNodes(nodeA, nodeB) {
    if (nodeA.firstChild == null || nodeB.firstChild == null) {
        return;
    }

    var textA = nodeA.firstChild.data;
    var linesA = textA.split(/\r?\n/);

    var textB = nodeB.firstChild.data;
    var linesB = textB.split(/\r?\n/);

    var result = [];
    for (var i = 0; i < linesB.length; i++) {
        var lineA = null;
        if (i < linesA.length) {
            lineA = linesA[i];
        }

        if (linesA[i] === linesB[i]) {
            result = result.concat(document.createTextNode(linesB[i]));
        } else {
            var wrapper = document.createElement("span");
            wrapper.setAttribute("class", "diff-mismatch");
            var text = document.createTextNode(linesB[i]);
            wrapper.appendChild(text);
            result = result.concat(wrapper);
        }
        if (i !== linesB.length-1) {
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
