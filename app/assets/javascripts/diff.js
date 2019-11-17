function diffNodes(nodeA, nodeB) {
    if (nodeA.firstChild === null || nodeB.firstChild === null) {
        return;
    }

    const linesA = nodeA.firstChild.data.split(/\r?\n/);
    const linesB = nodeB.firstChild.data.split(/\r?\n/);

    let result = [];
    for (let i = 0; i < linesB.length; i++) {
        if (i === linesB.length - 1 && linesB[i].length === 0) {
            // skip adding last, empty line because it's just a newline we added before
        } else if (linesA[i] === linesB[i]) {
            result = result.concat(document.createTextNode(linesB[i] + "\n"));
        } else {
            const wrapper = document.createElement("span");
            wrapper.setAttribute("class", "diff-mismatch");
            wrapper.appendChild(document.createTextNode(linesB[i]));
            result = result.concat(wrapper);
            result = result.concat(document.createTextNode("\n"));
        }
    }

    nodeB.removeChild(nodeB.firstChild);
    for (let j = 0; j < result.length; j++) {
        nodeB.appendChild(result[j]);
    }
}

function applyDiff() {
    const expected = $(".expected-output").get();
    const actual = $(".actual-output").get();
    const count = Math.min(expected.length, actual.length);

    for (let i = 0; i < count; i++) {
        diffNodes(expected[i], actual[i]);
    }
}
