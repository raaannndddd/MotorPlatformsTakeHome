import { describe } from './api';

export interface Notice {
  text: string;
  isError?: boolean;
}

export function failure(error: unknown): Notice {
  return { text: describe(error), isError: true };
}

/** A one-line success or error message under a form. */
export function Message({ notice }: { notice: Notice | null }) {
  return <p className={notice?.isError ? 'error' : 'ok'}>{notice?.text}</p>;
}
