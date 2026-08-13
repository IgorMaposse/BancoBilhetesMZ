export const environment = {
  production: true,
  // Os servicos do frontend ja constroem `${apiUrl}/api/v1/...`, por isso aqui
  // fica vazio: em producao o Nginx (ver nginx.conf) recebe os pedidos no
  // mesmo host e faz proxy_pass de /api/ para o ticketing-platform.
  apiUrl: '',
};
