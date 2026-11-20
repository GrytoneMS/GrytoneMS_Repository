import { useId } from 'react'
import type { ComponentPropsWithoutRef } from 'react'

type InputProps = ComponentPropsWithoutRef<'input'> & { label: string; error?: string }

export function Input({ label, error, id, className = '', 'aria-describedby': describedBy, ...props }: InputProps) {
  const generatedId = useId()
  const inputId = id ?? generatedId
  const errorId = `${inputId}-error`
  return (
    <div className="input-field">
      <label htmlFor={inputId}>{label}</label>
      <input {...props} id={inputId} className={`input ${className}`}
        aria-invalid={error ? true : props['aria-invalid']}
        aria-describedby={[describedBy, error ? errorId : undefined].filter(Boolean).join(' ') || undefined} />
      {error && <p className="field-error" id={errorId}>{error}</p>}
    </div>
  )
}
