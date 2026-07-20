import { createBrowserRouter, Navigate } from "react-router-dom";
import { AppLayout } from "../components/layout/AppLayout";
import { AdminPage } from "../features/admin/AdminPage";
import { AuthRedirect } from "../features/auth/AuthRedirect";
import { ProtectedRoute } from "../features/auth/ProtectedRoute";
import { AlertsPage } from "../features/alerts/AlertsPage";
import { ProductDetailPage } from "../features/prices/ProductDetailPage";
import { ResultsPage } from "../features/prices/ResultsPage";
import { SearchPage } from "../features/prices/SearchPage";
import { ProfilePage } from "../features/profile/ProfilePage";
import { WatchlistPage } from "../features/watchlist/WatchlistPage";
import { ForbiddenPage } from "../pages/ForbiddenPage";
import { NotFoundPage } from "../pages/NotFoundPage";

export const router = createBrowserRouter([
  {
    element: <AppLayout />,
    children: [
      { path: "/", element: <SearchPage /> },
      { path: "/results", element: <ResultsPage /> },
      { path: "/product/:productId", element: <ProductDetailPage /> },
      { path: "/403", element: <ForbiddenPage /> },
      { path: "/login", element: <AuthRedirect mode="login" /> },
      { path: "/register", element: <AuthRedirect mode="register" /> },
      {
        element: <ProtectedRoute />,
        children: [
          { path: "/app", element: <Navigate to="/app/watchlist" replace /> },
          { path: "/app/watchlist", element: <WatchlistPage /> },
          { path: "/app/alerts", element: <AlertsPage /> },
          { path: "/app/profile", element: <ProfilePage /> },
        ],
      },
      {
        element: <ProtectedRoute role="ADMIN" />,
        children: [{ path: "/admin", element: <AdminPage /> }],
      },
      { path: "*", element: <NotFoundPage /> },
    ],
  },
]);
