import type { AxiosError } from "axios";
import type { ProblemDetail } from "../types/api";

export const errorMessage = (error: unknown): string => {
  if (!error) return "An unexpected error occurred.";
  const axiosErr = error as AxiosError<ProblemDetail>;
  const data = axiosErr?.response?.data;
  if (data?.detail) {
    return data.detail;
  }
  if (data?.code) {
    return data.code;
  }
  if (axiosErr?.message) {
    return axiosErr.message;
  }
  if (typeof error === "string") {
    return error;
  }
  return "We could not complete that request. Please try again.";
};