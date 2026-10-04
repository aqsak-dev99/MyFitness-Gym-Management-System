import ReactMarkdown from 'react-markdown';
import styles from './MarkdownAnswer.module.css';

/**
 * Renders an AI answer as formatted text. Gemini writes Markdown
 * (###, **, *), which printed as raw symbols when the answer was
 * dropped into a plain <p>.
 *
 * react-markdown builds real React elements instead of injecting an
 * HTML string, and it ignores raw HTML in the source — so model output
 * can't inject markup or scripts into the page.
 */
export default function MarkdownAnswer({ text }) {
  return (
    <div className={styles.markdown}>
      <ReactMarkdown
        components={{
          a: ({ href, children }) => (
            <a href={href} target="_blank" rel="noopener noreferrer">{children}</a>
          ),
        }}
      >
        {text || ''}
      </ReactMarkdown>
    </div>
  );
}
