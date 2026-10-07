import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import styles from './MarkdownAnswer.module.css';

/**
 * Renders model output as formatted text. Gemini writes Markdown
 * (###, **, *), which printed as raw symbols when the answer was
 * dropped into a plain <p>.
 *
 * react-markdown builds real React elements instead of injecting an
 * HTML string, and it ignores raw HTML in the source — so model output
 * can't inject markup or scripts into the page.
 *
 * remark-gfm adds GitHub-style Markdown, mainly tables. Without it a
 * table (which the Admin AI uses for lists like overdue members) is not
 * recognised and prints as one long line of | pipes |. Tables are wrapped
 * in a scroll container so a wide one scrolls sideways on a phone
 * instead of stretching the page.
 */
export default function MarkdownAnswer({ text }) {
  return (
    <div className={styles.markdown}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          a: ({ href, children }) => (
            <a href={href} target="_blank" rel="noopener noreferrer">{children}</a>
          ),
          table: ({ children }) => (
            <div className={styles.tableWrap}>
              <table>{children}</table>
            </div>
          ),
        }}
      >
        {text || ''}
      </ReactMarkdown>
    </div>
  );
}
