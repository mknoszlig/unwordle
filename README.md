# Unwordle

I wrote this to get around the nerd snipe that is [wordle](https://www.powerlanguage.co.uk/wordle/) (It's awesome and fun, I just have neither the time to do it every day nor the willpower to resist). So here's a corpus based solution that i hope will let me sleep at night.

Also, I've wanted to take a look at the  [babashka task runner](https://book.babashka.org/#tasks) for a while now and this problem seems like a good opportunity.

## Requirements

- [https://github.com/babashka/babashka](babashka)
- `curl` and `tar` installed, to fetch and unpack the corpus.

## Usage

Commands are available as babashka tasks. Use `corpus` to fetch a corpus of words, or go directly to `solve` (will ensure the corpus is available if it's missing). Without additional information, it will return return what it deems to be the most likely solution. It will incorporate feedback to narrow down the search space. When you're done, you can clean everything up using `clean` (removes the corpus).

### Example

Let's say the wordle is `toast`.

We have no information so let's just guess a word (Tries to use one that uses frequently used letters as per the corpus).

```bash
> bb run solve
rates
```

we type it into wordle and get:

![black](https://abs-0.twimg.com/emoji/v2/72x72/2b1b.png) ![yellow](https://abs-0.twimg.com/emoji/v2/72x72/1f7e8.png) ![yellow](https://abs-0.twimg.com/emoji/v2/72x72/1f7e8.png) ![black](https://abs-0.twimg.com/emoji/v2/72x72/2b1b.png) ![yellow](https://abs-0.twimg.com/emoji/v2/72x72/1f7e8.png)

Nice! That's already quite a good start. OK, how do we pass that information back? We provide the feedback in the following form: the letter in our guess word (`r`, `a`, `t`, `e`, `s`) plus the feedback we got from wordle: we receive colored tiles, we'll be using the colors' first letter.

| tile | meaning | short |
|--|--|--|
| ![black](https://abs-0.twimg.com/emoji/v2/72x72/2b1b.png) | not present | `b` |
| ![yellow](https://abs-0.twimg.com/emoji/v2/72x72/1f7e8.png) | present | `y` |
| ![green](https://abs-0.twimg.com/emoji/v2/72x72/1f7e9.png) | correct | `g` |

So our `r` in first position that is absent from our target word is encoded as `rb`. For the `a` in second position that is found in the target word (but at a different position) we write `ay`. We follow through for each letter and pass this to the task:

```bash
> bb run solve rb ay ty eb sy
stain
```

![black](https://abs-0.twimg.com/emoji/v2/72x72/2b1b.png) ![yellow](https://abs-0.twimg.com/emoji/v2/72x72/1f7e8.png) ![green](https://abs-0.twimg.com/emoji/v2/72x72/1f7e9.png) ![black](https://abs-0.twimg.com/emoji/v2/72x72/2b1b.png) ![black](https://abs-0.twimg.com/emoji/v2/72x72/2b1b.png)

Finally! A correct placement!

We can just append this to our previous feedback to receive the next guess:

```bash
> bb run solve rb ay ty eb sy sy ty ag ib nb
coast
```

so close...

![black](https://abs-0.twimg.com/emoji/v2/72x72/2b1b.png) ![green](https://abs-0.twimg.com/emoji/v2/72x72/1f7e9.png) ![green](https://abs-0.twimg.com/emoji/v2/72x72/1f7e9.png) ![green](https://abs-0.twimg.com/emoji/v2/72x72/1f7e9.png) ![green](https://abs-0.twimg.com/emoji/v2/72x72/1f7e9.png)

```bash
> bb run solve rb ay ty eb sy sy ty ag ib nb cb og ag sg tg
toast
```

... and we're done. Enjoy!
