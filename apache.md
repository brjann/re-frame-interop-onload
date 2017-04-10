    <VirtualHost *:80>
       ServerName db1.localhost
       ProxyPass / http://localhost:3000/
         ServerName db2.localhost
       ProxyPass / http://localhost:3000/
         ServerName db3.localhost
       ProxyPass / http://localhost:3000/
    </VirtualHost>
