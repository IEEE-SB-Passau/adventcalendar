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
// This script will be loaded again when reloading the editor. Re-declaring variables is only possible for var, not for const and let.
var modelist = ace.require("ace/ext/modelist"); // jshint ignore:line
