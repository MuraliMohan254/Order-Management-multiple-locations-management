# ==========================================
# Frontend Dockerfile (Nginx Alpine)
# Assumes pre-built dist/ directory exists
# ==========================================
FROM nginx:alpine

# Remove default nginx static page
RUN rm -rf /usr/share/nginx/html/*

# Copy custom Nginx configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy pre-built Angular bundle (supports Angular 17+ browser/ directory)
COPY dist/order-management/browser/ /usr/share/nginx/html/

EXPOSE 80 443

CMD ["nginx", "-g", "daemon off;"]
