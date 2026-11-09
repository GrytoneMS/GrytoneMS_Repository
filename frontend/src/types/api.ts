export type FieldErrors = Record<string, string>

export type ApiSuccess<T> = { success: true; data: T }
export type ApiFailure = {
  success: false
  code: string
  message: string
  fieldErrors?: FieldErrors | null
}
export type ApiResponse<T> = ApiSuccess<T> | ApiFailure
