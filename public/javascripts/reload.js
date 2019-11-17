(function () {
    const whileQueuedInterval = 1000, noneQueuedInterval = 10000;
    const solutions = {};

    window.addEventListener("load", function () {

        $("#solutionlist").find(">div").each(function (i, element) {
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
            }).done(function (data) {
                let queuedSolutionPresent = false;
                let solutionList = data.solutionList;
                let evalRunning = data.evalRunning;
                let flash = data.flash;

                $.each(solutionList, function (i, solutionInfo) {
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
                            solution.element.find("#solution" + solutionInfo.id + " .testcase-collapse").each(function (i, element) {
                                if (wasTestcaseExpanded[element.id] !== undefined) {
                                    setExpandedStateTo($(this), wasTestcaseExpanded[element.id]);
                                }
                            });

                            // re-add syntax-highlighting
                            hljs.highlightBlock(solution.element.find("pre code")[0]);

                            // re-calculate diff
                            const expected = solution.element.find(".expected-output")[0];
                            const actual = solution.element.find(".actual-output")[0];
                            if (actual != null && expected != null) {
                                diffNodes(expected, actual);
                            }
                        }

                        if (solution.status === QUEUED) {
                            queuedSolutionPresent = true;
                        }
                    }
                });

                setTimer(queuedSolutionPresent);
            });
        }

        function setTimer(queuedSolutionPresent) {
            window.setTimeout(checkSolutionUpdates, (queuedSolutionPresent) ? whileQueuedInterval : noneQueuedInterval);
        }

        (function () {
            let queuedSolutionPresent = false;

            $.each(solutions, function (i, solution) {
                if (solution.status === QUEUED) {
                    queuedSolutionPresent = true;
                }
            });

            setTimer(queuedSolutionPresent);
        })();
    });
})();