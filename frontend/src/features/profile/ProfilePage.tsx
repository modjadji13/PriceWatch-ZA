import { useAuth } from "../auth/AuthProvider";

export function ProfilePage() {
  const { user } = useAuth();

  return (
    <section className="content-section narrow">
      <p className="eyebrow">Private</p>
      <h1>Profile</h1>
      <div className="profile-list">
        <div>
          <span>Name</span>
          <strong>{user?.displayName}</strong>
        </div>
        <div>
          <span>Email</span>
          <strong>{user?.email}</strong>
        </div>
        <div>
          <span>Role</span>
          <strong>{user?.role}</strong>
        </div>
      </div>
    </section>
  );
}
