export type UserRole = "USER" | "ADMIN";

export type AuthUser = {
  id: string;
  email: string;
  displayName: string;
  role: UserRole;
};

export type AuthStatus = "checking" | "anonymous" | "authenticated";

export type LoginPayload = {
  email: string;
  password: string;
};

export type RegisterPayload = LoginPayload & {
  displayName: string;
};
