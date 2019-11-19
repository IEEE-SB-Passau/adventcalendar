(() => {
    "use strict";
    const reloadIntervalFast = 1000, reloadIntervalSlow = 10000;
    const solutions = {};
    let queuedSolutionPresent = false;
    let evalRunning = true;
    let activeMessage;

    window.addEventListener("load", () => {

        $("#solutionlist").find(">div").each((i, element) => {
            element = $(element);
            solutions[element.attr("id")] = {status: element.data("state"), element: element};
        });

        function setExpandedStateTo(jqSelector, expanded) {
            if (expanded && jqSelector.attr("aria-expanded") !== "true") {
                jqSelector.addClass("in");
                jqSelector.attr("aria-expanded", "true");
            } else if (!expanded && jqSelector.attr("aria-expanded") === "true") {
                jqSelector.removeClass("in");
                jqSelector.attr("aria-expanded", "false");
            }
        }

        function checkSolutionUpdates() {
            $.ajax({
                url: apiUrl
            }).done(data => {
                evalRunning = data.evalRunning;
                const message = data.flash[0] === "live-alert" ? data.flash[1] : undefined;
                const solutionList = data.solutionList;


                if (activeMessage !== message) {
                    activeMessage = message;
                    $("#flash-messages > .live-alert").remove();
                    if (activeMessage !== undefined) {
                        $("#flash-messages").append(`<div class="alert alert-info live-alert">${message}</div>`);
                    }
                }

                queuedSolutionPresent = false;
                $.each(solutionList, (i, solutionInfo) => {
                    const solution = solutions[solutionInfo.id];

                    if (solution === undefined) {
                        return;
                    }

                    if (solution.status === QUEUED || solution.status !== solutionInfo.result) {
                        solution.status = solutionInfo.result;

                        if (solution.unprocessedHtml === undefined || solution.unprocessedHtml !== solutionInfo.html) {

                            // save expanded-states
                            const wasExpanded = solution.element.find("#solution" + solutionInfo.id).attr("aria-expanded") === "true";
                            const wasSourceExpanded = solution.element.find("#code" + solutionInfo.id).attr("aria-expanded") === "true";
                            const wasTestcaseExpanded = {};
                            solution.element.find("#solution" + solutionInfo.id + " .testcase-collapse").each(function (i, element) {
                                wasTestcaseExpanded[element.id] = $(this).attr("aria-expanded") === "true";
                            });

                            // update HTML
                            solution.unprocessedHtml = solutionInfo.html;
                            solution.element.html(solutionInfo.html);

                            // restore expanded-states
                            setExpandedStateTo(solution.element.find("#solution" + solutionInfo.id), wasExpanded);
                            setExpandedStateTo(solution.element.find("#code" + solutionInfo.id), wasSourceExpanded);
                            solution.element.find("#solution" + solutionInfo.id + " .testcase-collapse").each((i, element) => {
                                if (wasTestcaseExpanded[element.id] !== undefined) {
                                    setExpandedStateTo($(this), wasTestcaseExpanded[element.id]);
                                }
                            });

                            // re-add syntax-highlighting
                            hljs.highlightBlock(solution.element.find("pre code")[0]);

                            // re-calculate diff
                            const expected = solution.element.find(".expected-output")[0];
                            const actual = solution.element.find(".actual-output")[0];
                            if (actual !== undefined && expected !== undefined) {
                                diffNodes(expected, actual);
                            }
                        }

                        if (solution.status === QUEUED) {
                            queuedSolutionPresent = true;
                        }
                    }
                });

                setTimer();
            });
        }

        function setTimer() {
            window.setTimeout(checkSolutionUpdates, (queuedSolutionPresent && evalRunning) ? reloadIntervalFast : reloadIntervalSlow);
        }

        checkSolutionUpdates();
    });
})();
