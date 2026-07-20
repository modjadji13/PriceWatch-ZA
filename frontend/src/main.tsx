import React from "react";
import ReactDOM from "react-dom/client";
import { QueryClientProvider } from "@tanstack/react-query";
import { RouterProvider } from "react-router-dom";
import { AuthModalProvider } from "./features/auth/AuthModalProvider";
import { AuthProvider } from "./features/auth/AuthProvider";
import { queryClient } from "./app/queryClient";
import { router } from "./app/router";
import "./styles.css";

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <AuthModalProvider>
          <RouterProvider router={router} />
        </AuthModalProvider>
      </AuthProvider>
    </QueryClientProvider>
  </React.StrictMode>,
);
