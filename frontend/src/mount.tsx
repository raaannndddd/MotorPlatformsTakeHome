import { StrictMode, type ReactNode } from 'react';
import { createRoot } from 'react-dom/client';
import './app.css';
import logo from './assets/logo.png';

/** The logo shown in every page header. */
export function Brand() {
  return <img className="brand" src={logo} alt="MotorPlatform" />;
}

/** Renders a page's root component into the #root element of its HTML shell. */
export function mount(page: ReactNode) {
  createRoot(document.getElementById('root')!).render(<StrictMode>{page}</StrictMode>);
}
