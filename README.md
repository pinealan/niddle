# niddle

niddle is a [nREPL](https://github.com/nrepl/nrepl) middleware that prints
eval-ed forms and their corresponding results onto stdout of the nREPL server
process. One use-case for this middleware is when the nREPL server is started
non-interactively on the cli, but you'd like to keep track of all that has been
eval-ed on the same CLI. (think [Cursive
REPL](https://cursive-ide.com/userguide/repl.html))

Colored pretty printing is done with [puget](https://github.com/greglook/puget).
