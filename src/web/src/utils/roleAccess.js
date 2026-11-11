/** Frontend mirrors the backend separation; it never grants permission by itself. */
export function canManageProducts(role) {
  return role === 'SELLER'
}

export function canAccessRoleRoute(requiredRole, actualRole) {
  return !requiredRole || requiredRole === actualRole
}
