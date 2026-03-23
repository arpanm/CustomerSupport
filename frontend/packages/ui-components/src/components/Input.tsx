import { type InputHTMLAttributes, forwardRef } from 'react';
import { cn } from '../utils.js';

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
  helperText?: string;
}

export const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ className, label, error, helperText, id, type = 'text', ...props }, ref) => {
    const inputId = id ?? label?.toLowerCase().replace(/\s+/g, '-');

    return (
      <div className="flex flex-col gap-1">
        {label !== undefined && (
          <label
            htmlFor={inputId}
            className="text-sm font-medium leading-none text-gray-700 peer-disabled:cursor-not-allowed peer-disabled:opacity-70"
          >
            {label}
          </label>
        )}
        <input
          id={inputId}
          type={type}
          className={cn(
            'flex h-10 w-full rounded-md border bg-white px-3 py-2 text-sm ring-offset-white file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-gray-400 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50',
            error !== undefined
              ? 'border-red-500 focus-visible:ring-red-500'
              : 'border-gray-300 focus-visible:ring-blue-600',
            className,
          )}
          aria-invalid={error !== undefined}
          aria-describedby={
            error !== undefined
              ? `${inputId}-error`
              : helperText !== undefined
                ? `${inputId}-helper`
                : undefined
          }
          ref={ref}
          {...props}
        />
        {error !== undefined && (
          <p id={`${inputId}-error`} className="text-xs text-red-600" role="alert">
            {error}
          </p>
        )}
        {helperText !== undefined && error === undefined && (
          <p id={`${inputId}-helper`} className="text-xs text-gray-500">
            {helperText}
          </p>
        )}
      </div>
    );
  },
);

Input.displayName = 'Input';
