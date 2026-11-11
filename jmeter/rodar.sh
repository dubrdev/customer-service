#!/usr/bin/env bash
# Roda um plano do JMeter sem abrir a tela (modo texto) e gera um relatório em HTML.
#
#   ./jmeter/rodar.sh funcional
#   ./jmeter/rodar.sh carga -Jusers=50 -Jduracao=120
#
# Precisa do JMeter instalado: ou o comando "jmeter" está no PATH,
# ou a variável JMETER_HOME aponta para a pasta dele (ex.: export JMETER_HOME=/c/apache-jmeter-5.6.3).

set -e
cd "$(dirname "$0")"

teste="$1"
[ -n "$teste" ] && shift
case "$teste" in
  funcional) plano="01-funcional.jmx" ;;
  carga)     plano="02-carga.jmx" ;;
  *) echo "Uso: $0 funcional|carga [-Jparametro=valor ...]"; exit 1 ;;
esac

if [ -n "$JMETER_HOME" ]; then JMETER="$JMETER_HOME/bin/jmeter"; else JMETER="jmeter"; fi

# o JMeter só gera o relatório em pastas vazias, então limpamos o resultado anterior
mkdir -p resultados
rm -rf "resultados/$teste.jtl" "relatorio/$teste"

"$JMETER" -n -t "$plano" -l "resultados/$teste.jtl" -e -o "relatorio/$teste" "$@"

# conta as requisições que falharam (coluna 8 do .jtl = success)
falhas=$(awk -F, 'NR > 1 && $8 == "false"' "resultados/$teste.jtl" | wc -l)
echo
echo "Relatório: jmeter/relatorio/$teste/index.html"
if [ "$falhas" -gt 0 ]; then
  echo "ATENÇÃO: $falhas requisição(ões) falharam. Veja resultados/$teste.jtl"
  exit 1
fi
echo "Tudo certo: nenhuma falha."
