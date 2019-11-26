function loadSource(input) {
    const fileToLoad = input.files[0];
    const fileReader = new FileReader();
    fileReader.onload = function (fileLoadedEvent) {
        editor.setValue(fileLoadedEvent.target.result);
        submissiontext.value = fileLoadedEvent.target.result;
    };
    fileReader.readAsText(fileToLoad, "UTF-8");
}

function updateEditor() {
    editor.setOptions({
        enableBasicAutocompletion: true,
        enableSnippets: true,
        enableLiveAutocompletion: false
    });

    editor.getSession().on("change", function () {
        $('#submissiontext').val(editor.getSession().getValue());
    });

    editor.session.setMode(modelist.getModeForPath('file.' + langExtension).mode);
    if (solution.files.length > 0) {
        loadSource(solution);
    }
}

ace.require("ace/ext/language_tools");
const modelist = ace.require("ace/ext/modelist");
