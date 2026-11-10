// 1차 API 명세는 배열 형식. 기존 호출부의 Record 형식도 호환 유지합니다.
export type FieldErrors = { field: string; message: string }[] | Record<string, string>

export type ApiSuccess<T> = { success: true; data: T }
export type ApiFailure = {
  success: false
  code: string
  message: string
  fieldErrors?: FieldErrors | null
}
export type ApiResponse<T> = ApiSuccess<T> | ApiFailure
