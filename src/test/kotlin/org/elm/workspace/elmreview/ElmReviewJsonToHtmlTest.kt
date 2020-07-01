package org.elm.workspace.elmreview


import junit.framework.TestCase
import org.elm.lang.ElmTestBase
import org.intellij.lang.annotations.Language


class ElmReviewJsonToHtmlTest : ElmTestBase() {
    fun `test specific error`() {
        @Language("JSON")
        val json = """
            {
              "type": "review-errors",
              "errors": [
                {
                  "path": "src/NoUnused/Dependencies.elm",
                  "errors": [
                    {
                      "message": "Top-level variable `fzef` is not used",
                      "rule": "NoUnused.Variables",
                      "ruleLink": "https://package.elm-lang.org/packages/jfmengels/review-unused/2.1.0/NoUnused-Variables",
                      "details": [
                        "You should either use this value somewhere, or remove it at the location I pointed at."
                      ],
                      "region": {
                        "start": {
                          "line": 48,
                          "column": 1
                        },
                        "end": {
                          "line": 48,
                          "column": 5
                        }
                      },
                      "fix": [
                        {
                          "range": {
                            "start": {
                              "line": 48,
                              "column": 1
                            },
                            "end": {
                              "line": 50,
                              "column": 1
                            }
                          },
                          "str": ""
                        }
                      ],
                      "formatted": [
                        {
                          "str": "(fix) ",
                          "color": [
                            51,
                            187,
                            200
                          ]
                        },
                        {
                          "str": "NoUnused.Variables",
                          "color": [
                            255,
                            0,
                            0
                          ]
                        },
                        ": Top-level variable `fzef` is not used\n\n48| fzef =\n    ",
                        {
                          "str": "^^^^",
                          "color": [
                            255,
                            0,
                            0
                          ]
                        },
                        "\n49|     1\n\nYou should either use this value somewhere, or remove it at the location I pointed at."
                      ]
                    }
                  ]
                }
              ]
            }
            """.trimIndent()

        TestCase.assertEquals(elmReviewJsonToMessages(json),
                listOf(ElmReviewError(
                        path = "src/NoUnused/Dependencies.elm",
                        rule = "NoUnused.Variables",
                        ruleLink = "https://package.elm-lang.org/packages/jfmengels/review-unused/2.1.0/NoUnused-Variables",
                        message = "Top-level variable `fzef` is not used",
                        details = listOf("You should either use this value somewhere, or remove it at the location I pointed at."),
                        region = Region(Start(48, 1), End(48, 5)),
                        html = "<html><body style=\"font-family: monospace; font-weight: bold\">" +
                                "<span style=\"color: #33bbc8;\">(fix)&nbsp;</span>" +
                                "<span style=\"color: #FF5959;\">NoUnused.Variables</span>" +
                                "<span style=\"color: #4F9DA6\">:&nbsp;Top-level&nbsp;variable&nbsp;`fzef`&nbsp;is&nbsp;not&nbsp;used<br><br>48|&nbsp;fzef&nbsp;=<br>&nbsp;&nbsp;&nbsp;&nbsp;</span>" +
                                "<span style=\"color: #FF5959;\">^^^^</span>" +
                                "<span style=\"color: #4F9DA6\"><br>49|&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;1<br><br>You&nbsp;should&nbsp;either&nbsp;use&nbsp;this&nbsp;value&nbsp;somewhere,&nbsp;or&nbsp;remove&nbsp;it&nbsp;at&nbsp;the&nbsp;location&nbsp;I&nbsp;pointed&nbsp;at.</span>" +
                                "</body></html>"
                ))
        )
    }
}

