/* Complementos visuais não invasivos. Não substitui os controladores existentes. */
(function(){'use strict';
 const views={
 dashboard:['Centro de controlo','Operação logística em tempo real','i-dashboard'],
 carregamentos:['Gestão de carregamentos','Acompanhe os registos, estados e movimentações','i-truck'],
 'carregamento-detalhe':['Detalhes do carregamento','Documentos, localização e histórico operacional','i-truck'],
 alertas:['Central de alertas','Ocorrências, atrasos e eventos que exigem atenção','i-bell'],
 kpis:['Indicadores de desempenho','Analise os resultados e a eficiência da operação','i-chart'],
 relatorios:['Relatórios operacionais','Consulte e exporte os dados da operação','i-report'],
 utilizadores:['Gestão de utilizadores','Contas, perfis e permissões de acesso','i-users'],
 veiculos:['Gestão da frota','Veículos, disponibilidade e associação de motoristas','i-car'],
 fabricas:['Gestão de fábricas','Localizações, geofences e unidades operacionais','i-factory'],
 parametros:['Parâmetros do sistema','Configuração e regras da plataforma Logitrack','i-settings']};
 function render(){const main=document.querySelector('.content');if(!main||main.querySelector('.lt-hero'))return;const key=document.body.dataset.pagina||location.pathname.split('/').pop().replace('.html','');const v=views[key]||views[location.pathname.split('/').pop().replace('.html','')];if(!v)return;const role=document.body.dataset.perfilUi||'OPERADOR';const isAdmin=role==='ADMINISTRADOR';const page=document.createElement('div');page.className='lt-breadcrumb';const a=document.createElement('a');a.href=(window.LOGITRACK&&window.LOGITRACK.url?window.LOGITRACK.url('app/dashboard.html'):'app/dashboard.html');a.textContent='Logitrack';page.append(a);const sep=document.createElement('span');sep.textContent=' / ';page.append(sep);const current=document.createElement('span');current.textContent=v[0];page.append(current);const hero=document.createElement('section');hero.className='lt-hero';hero.setAttribute('aria-label','Resumo da área');const info=document.createElement('div');const eyebrow=document.createElement('div');eyebrow.className='lt-hero-eyebrow';eyebrow.textContent=isAdmin?'ADMINISTRAÇÃO · LOGITRACK':'OPERAÇÕES · LOGITRACK';const title=document.createElement('h2');title.textContent=key==='dashboard'?(isAdmin?'Painel do Administrador':'Painel do Operador'):v[0];const desc=document.createElement('p');desc.textContent=v[1];info.append(eyebrow,title,desc);const mark=document.createElement('div');mark.className='lt-hero-mark';mark.innerHTML='<svg aria-hidden="true"><use href="#'+v[2]+'"></use></svg>';hero.append(info,mark);const banner=main.querySelector('.role-banner');if(banner){banner.after(page,hero)}else{main.prepend(page,hero)}const footer=document.createElement('footer');footer.className='lt-footer';footer.innerHTML='<span>Logitrack · Gestão e Rastreabilidade de Carregamentos</span><span>Ambiente web · '+(isAdmin?'Administrador':'Operador')+'</span>';main.append(footer)}
 if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',render);else render();
})();
