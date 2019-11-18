(() => {
    "use strict";
    const knownAlertTypes = ["success", "info", "warning", "error"];
    const emptyMessageJson = JSON.stringify(["", ""]);
    const reloadIntervalFast = 1000, reloadIntervalSlow = 10000;
    const solutions = {};
    let queuedSolutionPresent = false;
    let evalRunning = true;
    let activeMessageJson;
    let floatingMessageContainer;

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
                const message = data.flash;
                const solutionList = data.solutionList;

                const messageJson = JSON.stringify(message);
                if (activeMessageJson !== messageJson) {
                    activeMessageJson = messageJson;
                    if (floatingMessageContainer !== undefined) {
                        floatingMessageContainer.remove();
                    }
                    if (activeMessageJson !== emptyMessageJson) {
                        const alertClass = "alert-" + (knownAlertTypes.includes(message[0]) ? message[0] : "info");
                        const alertHtml = `<div class="floating-alert-container"><div class="alert ${alertClass}">${message[1]}</div></div>`;
                        floatingMessageContainer = $("html > body > div.container > div.page-header").after(alertHtml)
                            .find("+.floating-alert-container");

                        // we need the stuck-attribute for styling, whenever the element is not in the normal position, but fixed
                        const observer = new IntersectionObserver(
                            ([e]) => e.target.toggleAttribute("stuck", e.intersectionRatio < 1),
                            {threshold: 1}
                        );
                        observer.observe(floatingMessageContainer[0]);
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
